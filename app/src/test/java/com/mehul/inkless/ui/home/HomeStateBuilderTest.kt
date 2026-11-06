package com.mehul.inkless.ui.home

import com.google.common.truth.Truth.assertThat
import com.mehul.inkless.data.db.dao.CategoryTotal
import com.mehul.inkless.data.db.dao.TransactionListItem
import com.mehul.inkless.data.db.entity.Direction
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
}
