package com.mehul.inkless.insights

import com.mehul.inkless.data.db.InklessDatabase
import com.mehul.inkless.data.db.dao.PaymentRow
import com.mehul.inkless.data.db.entity.RecurringEntity
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Finds payments that come round on a schedule: the same merchant, about the same amount, every
 * week, month or year (laundry on Sundays, a phone recharge, a yearly subscription). Daily chai at
 * the same tapri doesn't count; it's frequent, not scheduled.
 */
object Recurring {

    private data class Period(val days: Int, val tolerance: Int, val minPayments: Int)

    private val PERIODS = listOf(
        Period(7, 1, 4),
        Period(30, 3, 3),
        Period(365, 12, 2),
    )

    /** Amounts may wander this far from the usual one (laundry isn't the same every week). */
    private const val AMOUNT_TOLERANCE = 0.35

    /** Share of gaps and amounts that must fit, so one odd week doesn't break it. */
    private const val MUST_FIT = 0.75

    fun find(payments: List<PaymentRow>, now: Long, zone: ZoneId): List<RecurringEntity> =
        payments.filter { it.merchantId != null }
            .groupBy { it.merchantId!! }
            .mapNotNull { (merchantId, own) -> detect(merchantId, own, now, zone) }
            .sortedBy { it.nextExpectedAt }

    private fun detect(merchantId: String, payments: List<PaymentRow>, now: Long, zone: ZoneId): RecurringEntity? {
        // Two payments on one day are one visit.
        val visits = payments.sortedBy { it.timestamp }
            .distinctBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
        if (visits.size < 2) return null
        val days = visits.map { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
        val gaps = days.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b).toInt() }

        val period = PERIODS.firstOrNull { p ->
            visits.size >= p.minPayments && gaps.count { abs(it - p.days) <= p.tolerance } >= MUST_FIT * gaps.size
        } ?: return null

        val amounts = visits.map { it.amountPaise }
        val usual = Anomalies.median(amounts).toLong()
        if (amounts.count { abs(it - usual) <= usual * AMOUNT_TOLERANCE } < MUST_FIT * amounts.size) return null

        val last = visits.last()
        val next = Instant.ofEpochMilli(last.timestamp).plus(period.days.toLong(), ChronoUnit.DAYS).toEpochMilli()
        // Missed twice in a row: probably stopped (moved out, cancelled the subscription).
        if (now - last.timestamp > 2L * period.days * DAY_MS + period.tolerance * DAY_MS) return null
        return RecurringEntity(
            merchantId = merchantId,
            name = last.title ?: last.vpa ?: "Payment",
            categoryId = last.categoryId,
            typicalAmountPaise = usual,
            intervalDays = period.days,
            occurrences = visits.size,
            lastPaidAt = last.timestamp,
            nextExpectedAt = next,
        )
    }

    /** Looks over the last 13 months again and stores what comes round. */
    suspend fun refresh(db: InklessDatabase, now: Long, zone: ZoneId) {
        val dao = db.insightDao()
        dao.replaceRecurring(find(dao.paymentsSince(now - LOOK_BACK_DAYS * DAY_MS), now, zone))
    }

    private const val LOOK_BACK_DAYS = 400L
    private const val DAY_MS = 86_400_000L
}
