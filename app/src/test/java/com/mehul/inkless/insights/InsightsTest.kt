package com.mehul.inkless.insights

import com.google.common.truth.Truth.assertThat
import com.mehul.inkless.data.db.SpendPart
import com.mehul.inkless.data.db.dao.GuessOutcome
import com.mehul.inkless.data.db.dao.PaymentRow
import com.mehul.inkless.data.db.entity.PredictionSource
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

class InsightsTest {

    private val zone: ZoneId = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 7) // a Saturday

    private fun ms(day: LocalDate, hour: Int = 13) = day.atTime(hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun part(day: LocalDate, rupees: Long, category: String = "food_delivery") =
        SpendPart("t-$day-$rupees-$category", ms(day), null, category, rupees * 100)

    // Eight usual weeks of ₹500 of food delivery on Saturdays (one a bit more).
    private val usualWeeks = (1..8).map { part(today.minusWeeks(it.toLong()), if (it == 3) 650 else 500) }

    @Test
    fun `a week far above the usual is an alert`() {
        val alerts = Anomalies.find(usualWeeks + part(today, 1_300), today, zone)
        val week = alerts.single { it.period == AlertPeriod.WEEK }
        assertThat(week.usualPaise).isEqualTo(500_00)
        assertThat(week.spentPaise).isEqualTo(1_300_00)
        assertThat(AlertText.body(week)).isEqualTo("About 2.6× a usual week (₹500).")
    }

    @Test
    fun `a normal week, or a small jump, stays quiet`() {
        assertThat(Anomalies.find(usualWeeks + part(today, 550), today, zone)).isEmpty()
        // Twice the usual, but only ₹40 more: not worth a notification.
        val chai = (1..8).map { part(today.minusWeeks(it.toLong()), 40, "food_canteen") }
        assertThat(Anomalies.find(chai + part(today, 80, "food_canteen"), today, zone)).isEmpty()
    }

    @Test
    fun `a category without a habit has nothing to be unusual against`() {
        assertThat(Anomalies.find(listOf(part(today.minusWeeks(5), 300, "shopping"), part(today, 3_000, "shopping")), today, zone)).isEmpty()
        // A monthly recharge: three weeks out of eight, on schedule.
        val monthly = listOf(1, 5, 9).map { part(today.minusWeeks(it.toLong()), 349, "recharge") }
        assertThat(Anomalies.find(monthly + part(today, 349, "recharge"), today, zone)).isEmpty()
    }

    @Test
    fun `a day far above the everyday is an alert`() {
        val days = (1..28).map { part(today.minusDays(it.toLong()), 60, "food_canteen") }
        val alert = Anomalies.find(days + part(today, 400, "food_canteen"), today, zone).first { it.period == AlertPeriod.DAY }
        assertThat(alert.key).isEqualTo("food_canteen:day:2026-11-07")
    }

    private fun payment(day: LocalDate, rupees: Long, merchant: String, title: String = merchant, category: String = "recharge") =
        PaymentRow("p-$merchant-$day", ms(day), rupees * 100, category, merchant, title, null)

    @Test
    fun `a monthly recharge and weekly laundry are found, daily chai isn't`() {
        val recharge = (0..3).map { payment(LocalDate.of(2026, 8, 3).plusMonths(it.toLong()), 299, "jio", "Jio") }
        val laundry = (0..5).map { payment(today.minusWeeks(it.toLong()), if (it % 2 == 0) 120 else 150, "dhobi", "Dhobi", "laundry") }
        val chai = (0..20).map { payment(today.minusDays(it.toLong()), 20, "tapri", "Tapri", "food_canteen") }
        val found = Recurring.find(recharge + laundry + chai, ms(today), zone)
        assertThat(found.map { it.name }).containsExactly("Dhobi", "Jio")
        val jio = found.single { it.name == "Jio" }
        assertThat(jio.intervalDays).isEqualTo(30)
        assertThat(jio.typicalAmountPaise).isEqualTo(299_00)
        assertThat(found.single { it.name == "Dhobi" }.intervalDays).isEqualTo(7)
    }

    @Test
    fun `something not paid for two rounds has stopped`() {
        val old = (0..3).map { payment(LocalDate.of(2026, 3, 1).plusMonths(it.toLong()), 199, "netflix", "Netflix") }
        assertThat(Recurring.find(old, ms(today), zone)).isEmpty()
    }

    @Test
    fun `a month wrapped up in numbers`() {
        val month = YearMonth.of(2026, 10)
        val d = { day: Int -> LocalDate.of(2026, 10, day) }
        val parts = listOf(part(d(3), 400), part(d(4), 40, "food_canteen"), part(d(4), 20, "food_canteen"), part(d(10), 1_200, "shopping"))
        val payments = listOf(
            payment(d(3), 400, "swiggy", "Swiggy", "food_delivery"),
            payment(d(4), 40, "tapri", "Tapri", "food_canteen"),
            payment(d(4), 20, "tapri", "Tapri", "food_canteen").copy(id = "x", timestamp = ms(d(4), 23)),
            payment(d(10), 1_200, "amazon", "Amazon", "shopping"),
        )
        val stats = Wrapped.of(month, parts, listOf(part(LocalDate.of(2026, 9, 9), 1_000)), payments, today, zone)
        assertThat(stats.totalPaise).isEqualTo(1_660_00)
        assertThat(stats.change).isEqualTo(66)
        assertThat(stats.topCategoryId).isEqualTo("shopping")
        assertThat(stats.topCategoryShare).isEqualTo(72)
        assertThat(stats.mostVisited).isEqualTo("Tapri")
        assertThat(stats.mostVisitedTimes).isEqualTo(2)
        assertThat(stats.biggest?.title).isEqualTo("Amazon")
        assertThat(stats.small).isEqualTo(1)
        assertThat(stats.lateNight).isEqualTo(1)
        assertThat(stats.noSpendDays).isEqualTo(28)
        assertThat(stats.busiestWeekday).isEqualTo(DayOfWeek.SATURDAY)
    }

    @Test
    fun `accuracy counts the model's guesses and the payments changed after filing`() {
        val t = ms(today)
        val outcomes = listOf(
            GuessOutcome(t, PredictionSource.MODEL, "food_canteen", "food_canteen", "food_canteen"),
            GuessOutcome(t, PredictionSource.KEYWORD, "travel", "fun", "fun"),
            GuessOutcome(t, PredictionSource.NONE, null, "shopping", "college"),
            GuessOutcome(t, PredictionSource.NONE, null, null, "college"),
        )
        val report = AccuracyReport.of(outcomes, learnedFrom = 40, today = today, zone = zone)
        assertThat(report.guesses).isEqualTo(3)
        assertThat(report.right).isEqualTo(2)
        assertThat(report.filed).isEqualTo(2)
        assertThat(report.overridden).isEqualTo(1)
        assertThat(report.weeks.last().guesses).isEqualTo(3)
    }
}
