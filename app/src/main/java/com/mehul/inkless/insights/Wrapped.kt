package com.mehul.inkless.insights

import com.mehul.inkless.data.db.SpendPart
import com.mehul.inkless.data.db.dao.PaymentRow
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** A month in a few numbers, for the Wrapped card and the image it shares. */
data class WrappedStats(
    val month: YearMonth,
    val totalPaise: Long,
    val previousTotalPaise: Long,
    val payments: Int,
    val topCategoryId: String?,
    val topCategoryPaise: Long,
    val topPlace: String?,
    val topPlacePaise: Long,
    val mostVisited: String?,
    val mostVisitedTimes: Int,
    val biggest: PaymentRow?,
    val busiestWeekday: DayOfWeek?,
    val noSpendDays: Int,
    val lateNight: Int,
    /** Payments of ₹30 or less: chai, a samosa, a printout. */
    val small: Int,
) {
    /** Percent up (positive) or down on the month before; null with nothing to compare. */
    val change: Int? get() =
        if (previousTotalPaise <= 0) null else (((totalPaise - previousTotalPaise) * 100) / previousTotalPaise).toInt()

    val topCategoryShare: Int get() = if (totalPaise <= 0) 0 else ((topCategoryPaise * 100) / totalPaise).toInt()
}

object Wrapped {

    private const val SMALL_PAISE = 30_00L

    /**
     * [parts] and [payments] are the month's; [previousParts] the month before's. [today] cuts the
     * no-spend count short for the month still running.
     */
    fun of(
        month: YearMonth,
        parts: List<SpendPart>,
        previousParts: List<SpendPart>,
        payments: List<PaymentRow>,
        today: LocalDate,
        zone: ZoneId,
    ): WrappedStats {
        fun date(millis: Long) = Instant.ofEpochMilli(millis).atZone(zone)

        val topCategory = parts.filter { it.categoryId != null }.groupBy { it.categoryId!! }
            .mapValues { (_, list) -> list.sumOf { it.amountPaise } }
            .maxByOrNull { it.value }
        val byPlace = payments.filter { it.title != null }.groupBy { it.title!! }
        val topPlace = byPlace.mapValues { (_, list) -> list.sumOf { it.amountPaise } }.maxByOrNull { it.value }
        val mostVisited = byPlace.mapValues { it.value.size }.maxByOrNull { it.value }
        val busiest = parts.groupBy { date(it.timestamp).dayOfWeek }
            .mapValues { (_, list) -> list.sumOf { it.amountPaise } }
            .maxByOrNull { it.value }?.key

        val lastDay = if (YearMonth.from(today) == month) today.dayOfMonth else month.lengthOfMonth()
        val spendDays = parts.filter { it.amountPaise > 0 }.map { date(it.timestamp).toLocalDate() }.toSet()
        val noSpend = (1..lastDay).count { month.atDay(it) !in spendDays }

        return WrappedStats(
            month = month,
            totalPaise = parts.sumOf { it.amountPaise },
            previousTotalPaise = previousParts.sumOf { it.amountPaise },
            payments = payments.size,
            topCategoryId = topCategory?.key,
            topCategoryPaise = topCategory?.value ?: 0,
            topPlace = topPlace?.key,
            topPlacePaise = topPlace?.value ?: 0,
            mostVisited = mostVisited?.key,
            mostVisitedTimes = mostVisited?.value ?: 0,
            biggest = payments.maxByOrNull { it.amountPaise },
            busiestWeekday = busiest,
            noSpendDays = noSpend,
            lateNight = payments.count { date(it.timestamp).hour >= 22 || date(it.timestamp).hour < 4 },
            small = payments.count { it.amountPaise <= SMALL_PAISE },
        )
    }
}
