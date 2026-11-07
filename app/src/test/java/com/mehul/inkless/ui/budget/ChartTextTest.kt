package com.mehul.inkless.ui.budget

import com.google.common.truth.Truth.assertThat
import com.mehul.inkless.ml.WeekAccuracy
import com.mehul.inkless.ui.insights.accuracySummary
import java.time.LocalDate
import org.junit.Test

/** What TalkBack reads instead of each chart. */
class ChartTextTest {

    @Test
    fun `pace chart says where the month is and where it's heading`() {
        val spent = listOf(30_000L, 70_000L, 1_10_000L, 1_80_000L, 2_40_000L, 2_70_000L)
        assertThat(ChartText.pace(spent, 12_00_000, 30, 45_000))
            .isEqualTo("₹2,700 spent by day 6 of 30, of ₹12,000. At the recent pace, about ₹13,500 by the end.")
        assertThat(ChartText.pace(spent, 12_00_000, 30, 0)).isEqualTo("₹2,700 spent by day 6 of 30, of ₹12,000.")
        assertThat(ChartText.pace(emptyList(), 0, 30, 0)).isEqualTo("No allowance set")
    }

    @Test
    fun `month bars read oldest first, then this month and the budget`() {
        assertThat(ChartText.months(listOf(90_000, 1_20_000, 78_000), 3_00_000))
            .isEqualTo("Monthly totals, oldest first: ₹900, ₹1,200. This month ₹780. Budget ₹3,000.")
        assertThat(ChartText.months(listOf(78_000), null)).isEqualTo("This month ₹780.")
        assertThat(ChartText.months(emptyList(), null)).isEqualTo("No months yet")
    }

    @Test
    fun `accuracy chart reads each week's share`() {
        val monday = LocalDate.of(2026, 10, 19)
        val weeks = listOf(WeekAccuracy(monday, 0, 0), WeekAccuracy(monday.plusWeeks(1), 10, 7), WeekAccuracy(monday.plusWeeks(2), 10, 9))
        assertThat(accuracySummary(weeks)).isEqualTo("Right guesses each week, oldest first: none, 70%, 90%.")
    }
}
