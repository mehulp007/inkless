package com.mehul.inkless.insights

import com.mehul.inkless.data.db.SpendPart
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs

enum class AlertPeriod { DAY, WEEK }

/** "Food Delivery this week: ₹1,240, about 2.3× your usual ₹540." */
data class SpendAlert(
    val categoryId: String,
    val period: AlertPeriod,
    val spentPaise: Long,
    val usualPaise: Long,
    /** Which day or week it's about, so the same alert isn't sent twice. */
    val key: String,
) {
    val times: Float get() = if (usualPaise <= 0) 0f else spentPaise.toFloat() / usualPaise
}

/**
 * Spending that's way above a category's normal. "Normal" is the median of the last 8 weeks (or
 * 28 days), and "way above" uses the median absolute deviation, which a single big month can't
 * drag up the way an average and standard deviation would. On top of the statistics, an alert
 * needs the amount to be clearly bigger in plain rupees too, so ₹90 against a usual ₹40 stays quiet.
 */
object Anomalies {

    const val WEEKS_BACK = 8
    const val DAYS_BACK = 28
    private const val MIN_Z = 3.0
    private const val MIN_TIMES = 1.8
    private const val MIN_EXTRA_WEEK = 300_00L
    private const val MIN_EXTRA_DAY = 200_00L

    fun find(parts: List<SpendPart>, today: LocalDate, zone: ZoneId): List<SpendAlert> {
        val byCategory = parts.filter { it.categoryId != null }.groupBy { it.categoryId!! }
        return byCategory.flatMap { (category, own) ->
            val byDay = own.groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
                .mapValues { (_, list) -> list.sumOf { it.amountPaise } }
            listOfNotNull(week(category, byDay, today), day(category, byDay, today))
        }.sortedByDescending { it.times }
    }

    // This week so far against whole past weeks: an alert means it's already past a usual week.
    private fun week(category: String, byDay: Map<LocalDate, Long>, today: LocalDate): SpendAlert? {
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        fun weekTotal(start: LocalDate, end: LocalDate) =
            byDay.filterKeys { !it.isBefore(start) && !it.isAfter(end) }.values.sum()
        val now = weekTotal(monday, today)
        val history = (1..WEEKS_BACK).map { back -> monday.minusWeeks(back.toLong()).let { weekTotal(it, it.plusDays(6)) } }
        // A category needs a habit before anything can be unusual for it.
        if (history.count { it > 0 } < 3) return null
        return judge(category, AlertPeriod.WEEK, now, history, MIN_EXTRA_WEEK, "week:$monday")
    }

    private fun day(category: String, byDay: Map<LocalDate, Long>, today: LocalDate): SpendAlert? {
        val now = byDay[today] ?: 0
        val history = (1..DAYS_BACK).map { byDay[today.minusDays(it.toLong())] ?: 0 }
        // Daily alerts only for everyday categories; one shopping trip a month isn't news.
        if (history.count { it > 0 } < 7) return null
        return judge(category, AlertPeriod.DAY, now, history, MIN_EXTRA_DAY, "day:$today")
    }

    private fun judge(category: String, period: AlertPeriod, now: Long, history: List<Long>, minExtra: Long, key: String): SpendAlert? {
        val usual = median(history)
        // Usually nothing (a monthly recharge, say): it turning up isn't unusual, it's the schedule.
        if (usual <= 0.0) return null
        if (now - usual < minExtra || now < usual * MIN_TIMES) return null
        val spread = 1.4826 * middle(history.map { abs(it - usual) })
        // Every past week much the same: any clear jump counts.
        val z = if (spread == 0.0) Double.MAX_VALUE else (now - usual) / spread
        if (z < MIN_Z) return null
        return SpendAlert(category, period, now, usual.toLong(), "$category:$key")
    }

    fun median(values: List<Long>): Double = middle(values.map { it.toDouble() })

    private fun middle(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }
}
