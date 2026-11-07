package com.mehul.inkless.ui.home

import com.google.common.truth.Truth.assertThat
import com.mehul.inkless.data.db.dao.CategoryTotal
import com.mehul.inkless.data.db.dao.TransactionListItem
import com.mehul.inkless.data.db.entity.Direction
import com.mehul.inkless.data.db.entity.TransactionItemEntity
import com.mehul.inkless.data.db.entity.TransactionStatus
import com.mehul.inkless.data.db.entity.TransactionType
import com.mehul.inkless.data.seed.CategoryIds
import com.mehul.inkless.data.seed.DefaultCategories
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class HomeStateBuilderTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 11, 6)
    private val categories = DefaultCategories.create("demo", 0)

    private fun at(day: Int, hour: Int) =
        LocalDate.of(2026, 11, day).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun txn(
        id: String,
        amount: Long,
        timestamp: Long,
        categoryId: String?,
        direction: Direction = Direction.DEBIT,
        merchantName: String? = null,
        vpa: String? = "someone@ybl",
    ) = TransactionListItem(
        id = id,
        amountPaise = amount,
        direction = direction,
        type = if (direction == Direction.CREDIT) TransactionType.INCOME else TransactionType.EXPENSE,
        timestamp = timestamp,
        categoryId = categoryId,
        vpa = vpa,
        payeeRaw = vpa,
        merchantName = merchantName,
        status = TransactionStatus.AUTO,
    )

    // Newest first, the way the DAO returns them.
    private val transactions = listOf(
        txn("4", 3000, at(6, 17), CategoryIds.FOOD_CANTEEN, merchantName = "Tapri"),
        txn("3", 25000, at(5, 21), CategoryIds.FOOD_DELIVERY, merchantName = "Swiggy"),
        txn("2", 4000, at(5, 9), CategoryIds.FOOD_CANTEEN, merchantName = "Sharma Canteen"),
        txn("1", 1200000, at(1, 10), null, Direction.CREDIT, merchantName = "Home"),
    )
    private val totals = listOf(
        CategoryTotal(CategoryIds.FOOD_CANTEEN, 7000, 2),
        CategoryTotal(CategoryIds.FOOD_DELIVERY, 25000, 1),
    )

    private val state = HomeStateBuilder.build(YearMonth.of(2026, 11), today, zone, categories, transactions, totals)

    @Test
    fun `all tab first, then every category in order`() {
        assertThat(state.tabs.first().key).isEqualTo(HomeStateBuilder.ALL_TAB_KEY)
        assertThat(state.tabs.drop(1).map { it.key }).isEqualTo(categories.map { it.id })
    }

    @Test
    fun `month spend is the sum of category totals`() {
        assertThat(state.spentPaise).isEqualTo(32000)
        assertThat(state.tabs.first().totalPaise).isEqualTo(32000)
    }

    @Test
    fun `category tab only holds its own payments`() {
        val canteen = state.tabs.single { it.key == CategoryIds.FOOD_CANTEEN }
        assertThat(canteen.totalPaise).isEqualTo(7000)
        assertThat(canteen.count).isEqualTo(2)
        assertThat(canteen.days.flatMap { it.rows }.map { it.title }).containsExactly("Tapri", "Sharma Canteen").inOrder()
    }

    @Test
    fun `days are grouped newest first and credits don't count as spend`() {
        val all = state.tabs.first()
        assertThat(all.days.map { it.date.dayOfMonth }).containsExactly(6, 5, 1).inOrder()
        assertThat(all.days[1].spentPaise).isEqualTo(29000)
        assertThat(all.days[2].spentPaise).isEqualTo(0)
        assertThat(all.days[2].rows.single().isCredit).isTrue()
    }

    @Test
    fun `empty category still gets a tab`() {
        val gifts = state.tabs.single { it.key == CategoryIds.GIFTS }
        assertThat(gifts.totalPaise).isEqualTo(0)
        assertThat(gifts.days).isEmpty()
    }

    @Test
    fun `falls back to vpa when merchant is unknown and hides duplicate vpa`() {
        val unknown = HomeStateBuilder.build(
            YearMonth.of(2026, 11), today, zone, categories,
            listOf(txn("9", 500, at(6, 12), null, vpa = "q123@ybl")), emptyList(),
        ).tabs.first().days.single().rows.single()

        assertThat(unknown.title).isEqualTo("q123@ybl")
        assertThat(unknown.vpa).isNull()
        assertThat(unknown.emoji).isEqualTo("❓")
    }

    @Test
    fun `can only move forward when looking at a past month`() {
        assertThat(state.canGoForward).isFalse()
        val october = HomeStateBuilder.build(YearMonth.of(2026, 10), today, zone, categories, emptyList(), emptyList())
        assertThat(october.canGoForward).isTrue()
    }

    @Test
    fun `entries the bank named nobody for get a title from their type`() {
        val refund = txn("r", 28000, at(6, 12), CategoryIds.OTHER, Direction.CREDIT, vpa = null)
            .copy(type = TransactionType.REFUND)
        val income = txn("i", 50000, at(6, 11), null, Direction.CREDIT, vpa = null)
        val built = HomeStateBuilder.build(YearMonth.of(2026, 11), today, zone, categories, listOf(refund, income), emptyList())

        assertThat(built.tabs.first().days.flatMap { it.rows }.map { it.title })
            .containsExactly("Refund", "Money in").inOrder()
    }

    @Test
    fun `a broken-down payment is listed once, under its biggest item, and each tab counts its own part`() {
        val order = txn("amz", 500_000, at(6, 12), CategoryIds.SHOPPING, merchantName = "Amazon").copy(itemCount = 3)
        val items = listOf(
            TransactionItemEntity("i1", "amz", 300_000, CategoryIds.SHOPPING, "shoes", 0, "u1"),
            TransactionItemEntity("i2", "amz", 150_000, CategoryIds.COLLEGE, "calculator", 1, "u1"),
            TransactionItemEntity("i3", "amz", 50_000, CategoryIds.HEALTH, null, 2, "u1"),
        )
        val built = HomeStateBuilder.build(
            YearMonth.of(2026, 11), today, zone, categories, listOf(order),
            listOf(
                CategoryTotal(CategoryIds.SHOPPING, 300_000, 1),
                CategoryTotal(CategoryIds.COLLEGE, 150_000, 1),
                CategoryTotal(CategoryIds.HEALTH, 50_000, 1),
            ),
            items,
        )
        val tab = { id: String -> built.tabs.first { it.key == id } }

        val row = tab(CategoryIds.SHOPPING).days.single().rows.single()
        assertThat(row.moreItems).isEqualTo(2)
        assertThat(tab(CategoryIds.SHOPPING).days.single().spentPaise).isEqualTo(300_000)
        assertThat(tab(CategoryIds.COLLEGE).days).isEmpty()
        assertThat(tab(CategoryIds.COLLEGE).totalPaise).isEqualTo(150_000)
        assertThat(built.tabs.first().days.single().spentPaise).isEqualTo(500_000)
        assertThat(built.spentPaise).isEqualTo(500_000)
    }

    @Test
    fun `this month, days before yesterday fold into earlier`() {
        val list = listOf(
            txn("t", 3000, at(6, 17), CategoryIds.FOOD_CANTEEN, merchantName = "Tapri"),
            txn("y", 25000, at(5, 21), CategoryIds.FOOD_DELIVERY, merchantName = "Swiggy"),
            txn("c", 4000, at(4, 9), CategoryIds.FOOD_CANTEEN, merchantName = "Sharma Canteen"),
            txn("r", 39900, at(3, 9), CategoryIds.RECHARGE, merchantName = "Jio"),
            txn("s", 12000, at(2, 9), CategoryIds.COLLEGE, merchantName = "Xerox"),
            txn("a", 1_200_000, at(1, 10), null, Direction.CREDIT, merchantName = "Home"),
        )
        val all = HomeStateBuilder.build(YearMonth.of(2026, 11), today, zone, categories, list, emptyList()).tabs.first()

        assertThat(all.recent.map { it.date.dayOfMonth }).containsExactly(6, 5).inOrder()
        val earlier = all.earlier!!
        assertThat(earlier.from).isEqualTo(LocalDate.of(2026, 11, 2))
        assertThat(earlier.to).isEqualTo(LocalDate.of(2026, 11, 4))
        assertThat(earlier.paymentCount).isEqualTo(3)
        assertThat(earlier.spentPaise).isEqualTo(4000 + 39900 + 12000)
        assertThat(earlier.topCategories.first()).isEqualTo(categories.single { it.id == CategoryIds.RECHARGE }.name)
        assertThat(earlier.moreCategories).isFalse()
        // The allowance is money in, listed on its own and never folded into spending.
        assertThat(earlier.income.map { it.title }).containsExactly("Home")
        assertThat(earlier.days.map { it.date.dayOfMonth }).containsExactly(4, 3, 2).inOrder()
        // Every day is still there for anything that wants the whole month.
        assertThat(all.days.map { it.date.dayOfMonth }).containsExactly(6, 5, 4, 3, 2, 1).inOrder()
    }

    @Test
    fun `a past month isn't folded`() {
        val october = HomeStateBuilder.build(
            YearMonth.of(2026, 10), today, zone, categories,
            listOf(txn("o", 5000, LocalDate.of(2026, 10, 3).atTime(9, 0).atZone(zone).toInstant().toEpochMilli(), CategoryIds.TRAVEL)),
            emptyList(),
        ).tabs.first()
        assertThat(october.earlier).isNull()
        assertThat(october.recent).isEqualTo(october.days)
    }

    @Test
    fun `a refund makes the day's total net of it`() {
        val refund = txn("rf", 12000, at(5, 22), CategoryIds.FOOD_DELIVERY, Direction.CREDIT, merchantName = "Swiggy refund")
            .copy(type = TransactionType.REFUND)
        val built = HomeStateBuilder.build(YearMonth.of(2026, 11), today, zone, categories, transactions + refund, totals)
        val yesterday = built.tabs.first().days.single { it.date.dayOfMonth == 5 }
        assertThat(yesterday.net).isTrue()
        assertThat(yesterday.spentPaise).isEqualTo(29000 - 12000)
        assertThat(yesterday.rows.single { it.id == "rf" }.isRefund).isTrue()
        val delivery = built.tabs.single { it.key == CategoryIds.FOOD_DELIVERY }.days.single()
        assertThat(delivery.spentPaise).isEqualTo(25000 - 12000)
        // Today has no refund, so its total is plain spending.
        assertThat(built.tabs.first().days.first().net).isFalse()
    }

    @Test
    fun `rows know their category's name and whether they wait to be sorted`() {
        val waiting = txn("w", 5000, at(6, 18), CategoryIds.OTHER).copy(status = TransactionStatus.NEEDS_REVIEW)
        val row = HomeStateBuilder.build(YearMonth.of(2026, 11), today, zone, categories, listOf(waiting), emptyList())
            .tabs.first().days.single().rows.single()
        assertThat(row.toSort).isTrue()
        assertThat(row.categoryName).isEqualTo(categories.single { it.id == CategoryIds.OTHER }.name)
    }
}
