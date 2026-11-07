package com.mehul.inkless.ui.home

import com.mehul.inkless.budget.BudgetSnapshot
import com.mehul.inkless.budget.Cycle
import com.mehul.inkless.budget.Runway
import com.mehul.inkless.data.db.dao.CategoryTotal
import com.mehul.inkless.data.db.dao.TransactionListItem
import com.mehul.inkless.data.db.entity.CaptureSource
import com.mehul.inkless.data.db.entity.Direction
import com.mehul.inkless.data.db.entity.TransactionStatus
import com.mehul.inkless.data.db.entity.TransactionType
import com.mehul.inkless.data.seed.CategoryIds
import com.mehul.inkless.data.seed.DefaultCategories
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** The Figma Home's week in November, as Home's state builder would get it. */
internal object HomeFixtures {
    val zone: ZoneId = ZoneId.systemDefault()
    val today: LocalDate = LocalDate.of(2026, 11, 6)
    val categories = DefaultCategories.create("demo", 0)

    private fun at(day: Int, hour: Int, minute: Int = 0) =
        LocalDate.of(2026, 11, day).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun txn(
        id: String,
        rupees: Long,
        timestamp: Long,
        category: String?,
        name: String,
        direction: Direction = Direction.DEBIT,
        type: TransactionType = TransactionType.EXPENSE,
        status: TransactionStatus = TransactionStatus.AUTO,
        itemCount: Int = 0,
    ) = TransactionListItem(
        id = id, amountPaise = rupees * 100, direction = direction, type = type, timestamp = timestamp,
        categoryId = category, vpa = null, payeeRaw = name, merchantName = name, status = status,
        itemCount = itemCount, sources = setOf(CaptureSource.SMS),
    )

    val transactions = listOf(
        txn("h", 30, at(6, 18, 15), CategoryIds.OTHER, "Hostel QR · 4102", status = TransactionStatus.NEEDS_REVIEW),
        txn("q", 50, at(6, 17, 40), CategoryIds.OTHER, "Campus QR · 8291", status = TransactionStatus.NEEDS_REVIEW),
        txn("r", 100, at(6, 17, 5), CategoryIds.TRAVEL, "Rapido"),
        txn("c", 30, at(6, 16, 30), CategoryIds.FOOD_CANTEEN, "Block C Chai"),
        txn("s", 90, at(6, 13, 10), CategoryIds.FOOD_CANTEEN, "Sharma Canteen"),
        txn("sbi", 1500, at(5, 20), null, "To my SBI account", type = TransactionType.SELF_TRANSFER),
        txn("auto", 80, at(5, 19), CategoryIds.TRAVEL, "Campus auto"),
        txn("l", 200, at(5, 18), CategoryIds.LAUNDRY, "Hostel Laundry"),
        txn("sr", 120, at(5, 17), CategoryIds.FOOD_DELIVERY, "Swiggy refund", Direction.CREDIT, TransactionType.REFUND),
        txn("sw", 240, at(5, 13), CategoryIds.FOOD_DELIVERY, "Swiggy"),
        txn("b", 420, at(5, 11), CategoryIds.GROCERIES, "Blinkit", itemCount = 4),
        txn("e3", 600, at(4, 12), CategoryIds.FOOD_CANTEEN, "Night Mess"),
        txn("e2", 399, at(3, 12), CategoryIds.RECHARGE, "Jio"),
        txn("e1", 581, at(2, 12), CategoryIds.COLLEGE, "Xerox Point"),
        txn("a", 12_000, at(1, 10), null, "Monthly allowance", Direction.CREDIT, TransactionType.INCOME),
    )

    private val totals = listOf(
        CategoryTotal(CategoryIds.FOOD_CANTEEN, 72_000, 3),
        CategoryTotal(CategoryIds.TRAVEL, 18_000, 2),
        CategoryTotal(CategoryIds.OTHER, 8_000, 2),
        CategoryTotal(CategoryIds.LAUNDRY, 20_000, 1),
        CategoryTotal(CategoryIds.FOOD_DELIVERY, 12_000, 2),
        CategoryTotal(CategoryIds.GROCERIES, 42_000, 1),
        CategoryTotal(CategoryIds.RECHARGE, 39_900, 1),
        CategoryTotal(CategoryIds.COLLEGE, 58_100, 1),
    )

    val state = HomeStateBuilder.build(YearMonth.of(2026, 11), today, zone, categories, transactions, totals)

    val snapshot = BudgetSnapshot(
        cycle = Cycle(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 1)),
        today = today,
        budgetPaise = 1_200_000,
        spentPaise = 270_000,
        spentTodayPaise = 30_000,
        todaysSharePaise = 38_400,
        safeToSpendPaise = 8_400,
        runway = Runway.RunsOut(LocalDate.of(2026, 11, 27)),
        cumulativePaise = emptyList(),
        foodTodayPaise = 12_000,
        dailyFoodCapPaise = null,
        daysOverFoodCap = 0,
        pacePaisePerDay = 45_000,
    )
}
