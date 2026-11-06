package com.mehul.inkless.budget

import com.google.common.truth.Truth.assertThat
import com.mehul.inkless.data.db.SpendPart
import com.mehul.inkless.data.seed.CategoryIds
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class BudgetMathTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val food = setOf(CategoryIds.FOOD_CANTEEN, CategoryIds.FOOD_DELIVERY)

    private fun spend(day: LocalDate, rupees: Long, category: String = CategoryIds.TRAVEL, hour: Int = 13) =
        SpendPart("t$day$rupees$category", day.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli(), null, category, rupees * 100)

    @Test
    fun `a cycle runs from one allowance day to the next`() {
        assertThat(BudgetMath.cycle(LocalDate.of(2026, 11, 6), 5))
            .isEqualTo(Cycle(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 12, 5)))
        assertThat(BudgetMath.cycle(LocalDate.of(2026, 11, 3), 5))
            .isEqualTo(Cycle(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 11, 5)))
        // No allowance day: calendar months.
        assertThat(BudgetMath.cycle(LocalDate.of(2026, 11, 6), null))
            .isEqualTo(Cycle(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 1)))
    }

    @Test
    fun `an allowance on the 31st arrives on the last day of shorter months`() {
        val cycle = BudgetMath.cycle(LocalDate.of(2026, 2, 28), 31)
        assertThat(cycle).isEqualTo(Cycle(LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31)))
        assertThat(BudgetMath.cycle(LocalDate.of(2026, 2, 27), 31).start).isEqualTo(LocalDate.of(2026, 1, 31))
    }

    @Test
    fun `today's share is what was left this morning over the days left`() {
        // ₹12,000 budget, ₹6,000 gone before today, 10 days left including today, ₹200 spent today.
        val (share, safe) = BudgetMath.safeToSpend(1_200_000, 600_000, 20_000, daysLeft = 10)
        assertThat(share).isEqualTo(60_000)
        assertThat(safe).isEqualTo(40_000)
        // Overspent today: negative.
        assertThat(BudgetMath.safeToSpend(1_200_000, 600_000, 90_000, 10).second).isEqualTo(-30_000)
        // Budget already gone: nothing to share.
        assertThat(BudgetMath.safeToSpend(1_200_000, 1_300_000, 0, 10).first).isEqualTo(0)
    }

    @Test
    fun `the pace leans towards the latest days`() {
        assertThat(BudgetMath.pace(listOf(100L, 100L, 100L))).isWithin(0.001).of(100.0)
        val spree = BudgetMath.pace(listOf(100L, 100L, 1_000L))
        assertThat(spree).isWithin(0.001).of(370.0)
        assertThat(BudgetMath.pace(emptyList())).isEqualTo(0.0)
    }

    @Test
    fun `runway says when the money runs out at this pace`() {
        val cycle = Cycle(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 1))
        val today = LocalDate.of(2026, 11, 10)
        // ₹1,000 left at ₹100 a day: covers the 11th to the 20th, out on the 21st.
        assertThat(BudgetMath.runway(100_000, 10_000.0, today, cycle)).isEqualTo(Runway.RunsOut(LocalDate.of(2026, 11, 21)))
        assertThat(BudgetMath.runway(1_000_000, 10_000.0, today, cycle)).isEqualTo(Runway.Lasts)
        assertThat(BudgetMath.runway(0, 10_000.0, today, cycle)).isEqualTo(Runway.Spent)
        assertThat(BudgetMath.runway(100_000, 0.0, today, cycle)).isEqualTo(Runway.Lasts)
    }

    @Test
    fun `the snapshot counts only this cycle, with refunds taking away`() {
        val today = LocalDate.of(2026, 11, 6)
        val parts = listOf(
            spend(LocalDate.of(2026, 10, 30), 999), // last cycle
            spend(LocalDate.of(2026, 11, 2), 1_000),
            spend(LocalDate.of(2026, 11, 3), 500, CategoryIds.FOOD_DELIVERY),
            spend(LocalDate.of(2026, 11, 3), -200, CategoryIds.FOOD_DELIVERY), // refund
            spend(today, 150, CategoryIds.FOOD_CANTEEN),
            spend(today, 60, CategoryIds.FOOD_CANTEEN, hour = 20),
        )
        val snap = BudgetMath.snapshot(1_200_000, 1, 20_000, parts, food, today, zone)

        assertThat(snap.cycle.start).isEqualTo(LocalDate.of(2026, 11, 1))
        assertThat(snap.spentPaise).isEqualTo(151_000)
        assertThat(snap.spentTodayPaise).isEqualTo(21_000)
        // (12,000 − 1,300) over 25 days left = ₹428, minus ₹210 today.
        assertThat(snap.todaysSharePaise).isEqualTo(42_800)
        assertThat(snap.safeToSpendPaise).isEqualTo(21_800)
        assertThat(snap.cumulativePaise).containsExactly(0L, 100_000L, 130_000L, 130_000L, 130_000L, 151_000L).inOrder()
        assertThat(snap.foodTodayPaise).isEqualTo(21_000)
        // Over the ₹200 food cap on the 3rd (₹300 net) and today (₹210).
        assertThat(snap.daysOverFoodCap).isEqualTo(2)
        assertThat(snap.runway).isEqualTo(Runway.Lasts)
    }

    @Test
    fun `semesters are July to December and January to June`() {
        assertThat(BudgetMath.semester(LocalDate.of(2026, 11, 6)))
            .isEqualTo(LocalDate.of(2026, 7, 1) to LocalDate.of(2027, 1, 1))
        assertThat(BudgetMath.semester(LocalDate.of(2026, 3, 1)))
            .isEqualTo(LocalDate.of(2026, 1, 1) to LocalDate.of(2026, 7, 1))
        assertThat(BudgetMath.semesterName(LocalDate.of(2026, 11, 6))).isEqualTo("Jul–Dec 2026")
    }
}
