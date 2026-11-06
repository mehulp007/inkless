package com.mehul.inkless.budget

import com.google.common.truth.Truth.assertThat
import com.mehul.inkless.data.db.SpendPart
import com.mehul.inkless.data.db.entity.CategoryEntity
import com.mehul.inkless.data.seed.CategoryIds
import com.mehul.inkless.data.seed.DefaultCategories
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class BudgetTextTest {

    private val today = LocalDate.of(2026, 11, 20)
    private val cycle = Cycle(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 1))

    private fun snapshot(spent: Long = 600_000, safe: Long = 30_000, runway: Runway = Runway.Lasts, foodCap: Long? = null, over: Int = 0) =
        BudgetSnapshot(
            cycle = cycle, today = today, budgetPaise = 1_200_000, spentPaise = spent, spentTodayPaise = 0,
            todaysSharePaise = safe, safeToSpendPaise = safe, runway = runway, cumulativePaise = emptyList(),
            foodTodayPaise = 15_000, dailyFoodCapPaise = foodCap, daysOverFoodCap = over,
        )

    @Test
    fun `safe to spend, or by how much today's share is gone`() {
        assertThat(BudgetText.safeToSpendAmount(snapshot(safe = 54_500))).isEqualTo("₹545")
        assertThat(BudgetText.safeToSpendLabel(snapshot(safe = 54_500))).isEqualTo("safe to spend today")
        assertThat(BudgetText.safeToSpendAmount(snapshot(safe = -12_000))).isEqualTo("₹120")
        assertThat(BudgetText.safeToSpendLabel(snapshot(safe = -12_000))).isEqualTo("over today's share")
    }

    @Test
    fun `runway in words`() {
        assertThat(BudgetText.runway(snapshot())).isEqualTo("At this pace it lasts until your allowance on the 1st.")
        assertThat(BudgetText.runway(snapshot(runway = Runway.RunsOut(LocalDate.of(2026, 11, 24)))))
            .isEqualTo("At this pace you'll run out on the 24th.")
        assertThat(BudgetText.runway(snapshot(runway = Runway.Spent))).isEqualTo("This month's allowance is used up.")
        assertThat(BudgetText.spentOfBudget(snapshot())).isEqualTo("₹6,000 of ₹12,000 spent · 11 days to allowance")
    }

    @Test
    fun `food cap line only when a cap is set`() {
        assertThat(BudgetText.food(snapshot())).isNull()
        assertThat(BudgetText.food(snapshot(foodCap = 20_000, over = 3))).isEqualTo("Food today ₹150 of ₹200 · over on 3 days this month")
    }

    @Test
    fun `ordinals`() {
        assertThat(listOf(1, 2, 3, 4, 11, 12, 13, 21, 22, 23, 31).map { BudgetText.ordinal(LocalDate.of(2026, 1, it)) })
            .containsExactly("1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "21st", "22nd", "23rd", "31st").inOrder()
    }

    @Test
    fun `the weekly summary lists the top three categories and what's left`() {
        val categories: List<CategoryEntity> = DefaultCategories.create("u1", 0)
        fun part(category: String, rupees: Long) = SpendPart("t$category$rupees", 0, null, category, rupees * 100)
        val week = listOf(
            part(CategoryIds.FOOD_CANTEEN, 900), part(CategoryIds.TRAVEL, 600), part(CategoryIds.FOOD_DELIVERY, 450),
            part(CategoryIds.RECHARGE, 199), part(CategoryIds.FOOD_DELIVERY, -50),
        )
        val message = WeeklyDigest.compose(week, categories, snapshot())!!
        assertThat(message.title).isEqualTo("This week: ₹2,099 spent")
        assertThat(message.text).isEqualTo(
            "🍔 Food & Canteen ₹900 · 🚕 Travel ₹600 · 🍜 Food Delivery ₹400\n₹6,000 left until your allowance on the 1st.",
        )
        assertThat(WeeklyDigest.compose(emptyList(), categories, null)).isNull()
    }

    @Test
    fun `the summary goes out on Sunday at 7 in the evening`() {
        val zone = ZoneId.of("Asia/Kolkata")
        val friday = ZonedDateTime.of(2026, 11, 20, 10, 0, 0, 0, zone)
        val wait = DigestScheduler.untilNextSundayEvening(friday)
        val at = friday.plus(wait)
        assertThat(at.dayOfWeek).isEqualTo(DayOfWeek.SUNDAY)
        assertThat(at.hour).isEqualTo(19)
        val sundayNight = ZonedDateTime.of(2026, 11, 22, 21, 0, 0, 0, zone)
        assertThat(sundayNight.plus(DigestScheduler.untilNextSundayEvening(sundayNight)).toLocalDate()).isEqualTo(LocalDate.of(2026, 11, 29))
    }
}
