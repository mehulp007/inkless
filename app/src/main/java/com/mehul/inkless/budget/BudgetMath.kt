package com.mehul.inkless.budget

import com.mehul.inkless.data.db.SpendPart
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** From the day the allowance arrives up to (not including) the day the next one does. */
data class Cycle(val start: LocalDate, val endExclusive: LocalDate) {
    val days: Int get() = ChronoUnit.DAYS.between(start, endExclusive).toInt()

    /** Days still to live on, today included. */
    fun daysLeft(today: LocalDate): Int = ChronoUnit.DAYS.between(today, endExclusive).toInt().coerceAtLeast(1)

    fun startMillis(zone: ZoneId) = start.atStartOfDay(zone).toInstant().toEpochMilli()
    fun endMillis(zone: ZoneId) = endExclusive.atStartOfDay(zone).toInstant().toEpochMilli()
}

sealed interface Runway {
    /** At this pace the money lasts until the next allowance. */
    data object Lasts : Runway

    /** At this pace it runs out on [date], before the next allowance. */
    data class RunsOut(val date: LocalDate) : Runway

    /** This cycle's budget is already used up. */
    data object Spent : Runway
}

/** Where the user stands in the current cycle. All amounts in paise. */
data class BudgetSnapshot(
    val cycle: Cycle,
    val today: LocalDate,
    val budgetPaise: Long,
    val spentPaise: Long,
    val spentTodayPaise: Long,
    /** An even share of what was left this morning, over the days left. */
    val todaysSharePaise: Long,
    /** What can still be spent today; negative once today's share is overspent. */
    val safeToSpendPaise: Long,
    val runway: Runway,
    /** Spending so far by day of the cycle, added up: one entry per day from the start to today. */
    val cumulativePaise: List<Long>,
    val foodTodayPaise: Long,
    val dailyFoodCapPaise: Long?,
    /** Days this cycle with more food spending than the cap: meals skipped at the mess. */
    val daysOverFoodCap: Int,
) {
    val remainingPaise: Long get() = budgetPaise - spentPaise
    val usedFraction: Float get() = if (budgetPaise <= 0) 0f else (spentPaise.toFloat() / budgetPaise).coerceAtLeast(0f)
}

/**
 * The budget arithmetic, free of Android and the database so it's easy to test. Spending comes in
 * as [SpendPart]s (refunds negative, broken-down payments already split), never whole payments.
 */
object BudgetMath {

    /** How much the most recent days weigh in the spending pace (an exponentially weighted average). */
    const val PACE_WEIGHT = 0.3

    /** The cycle [today] falls in. No allowance day means calendar months. */
    fun cycle(today: LocalDate, allowanceDay: Int?): Cycle {
        val day = allowanceDay ?: 1
        val thisMonth = dayIn(YearMonth.from(today), day)
        val start = if (today >= thisMonth) thisMonth else dayIn(YearMonth.from(today).minusMonths(1), day)
        return Cycle(start, dayIn(YearMonth.from(start).plusMonths(1), day))
    }

    /** Net spending per day (refunds take away), for every day from the cycle start to [today]. */
    fun dailySpend(parts: List<SpendPart>, zone: ZoneId, from: LocalDate, today: LocalDate): List<Long> {
        val byDay = parts.groupBy { dateOf(it.timestamp, zone) }.mapValues { (_, p) -> p.sumOf { it.amountPaise } }
        return generateSequence(from) { it.plusDays(1) }.takeWhile { it <= today }.map { byDay[it] ?: 0L }.toList()
    }

    /**
     * Today's fair share: what was left this morning spread evenly over the days left, minus what
     * today has already taken.
     */
    fun safeToSpend(budgetPaise: Long, spentBeforeTodayPaise: Long, spentTodayPaise: Long, daysLeft: Int): Pair<Long, Long> {
        val share = (budgetPaise - spentBeforeTodayPaise).coerceAtLeast(0) / daysLeft.coerceAtLeast(1)
        return share to share - spentTodayPaise
    }

    /** Typical daily spending lately: recent days count most, so a spree shows up quickly. */
    fun pace(daily: List<Long>): Double {
        if (daily.isEmpty()) return 0.0
        var average = daily.first().toDouble()
        for (day in daily.drop(1)) average = PACE_WEIGHT * day + (1 - PACE_WEIGHT) * average
        return average.coerceAtLeast(0.0)
    }

    /** When the money left runs out at [pacePaisePerDay], counting from tomorrow. */
    fun runway(remainingPaise: Long, pacePaisePerDay: Double, today: LocalDate, cycle: Cycle): Runway {
        if (remainingPaise <= 0) return Runway.Spent
        if (pacePaisePerDay <= 0.0) return Runway.Lasts
        val fullDays = (remainingPaise / pacePaisePerDay).toLong()
        val runsOut = today.plusDays(fullDays + 1)
        return if (runsOut >= cycle.endExclusive) Runway.Lasts else Runway.RunsOut(runsOut)
    }

    fun snapshot(
        budgetPaise: Long,
        allowanceDay: Int?,
        dailyFoodCapPaise: Long?,
        parts: List<SpendPart>,
        foodCategoryIds: Set<String>,
        today: LocalDate,
        zone: ZoneId,
    ): BudgetSnapshot {
        val cycle = cycle(today, allowanceDay)
        val inCycle = parts.filter { dateOf(it.timestamp, zone).let { d -> d >= cycle.start && d <= today } }
        val daily = dailySpend(inCycle, zone, cycle.start, today)
        val spentToday = daily.last()
        val spent = daily.sum()
        val (share, safe) = safeToSpend(budgetPaise, spent - spentToday, spentToday, cycle.daysLeft(today))
        val food = dailySpend(inCycle.filter { it.categoryId in foodCategoryIds }, zone, cycle.start, today)
        return BudgetSnapshot(
            cycle = cycle,
            today = today,
            budgetPaise = budgetPaise,
            spentPaise = spent,
            spentTodayPaise = spentToday,
            todaysSharePaise = share,
            safeToSpendPaise = safe,
            runway = runway(budgetPaise - spent, pace(daily), today, cycle),
            cumulativePaise = daily.runningReduce(Long::plus),
            foodTodayPaise = food.last(),
            dailyFoodCapPaise = dailyFoodCapPaise,
            daysOverFoodCap = dailyFoodCapPaise?.let { cap -> food.count { it > cap } } ?: 0,
        )
    }

    /** The semester [day] is in: July to December, or January to June. */
    fun semester(day: LocalDate): Pair<LocalDate, LocalDate> {
        val firstHalf = day.monthValue <= Month.JUNE.value
        val start = LocalDate.of(day.year, if (firstHalf) Month.JANUARY else Month.JULY, 1)
        return start to start.plusMonths(6)
    }

    fun semesterName(day: LocalDate): String {
        val (start, _) = semester(day)
        return if (start.month == Month.JANUARY) "Jan–Jun ${start.year}" else "Jul–Dec ${start.year}"
    }

    private fun dayIn(month: YearMonth, day: Int): LocalDate = month.atDay(day.coerceIn(1, month.lengthOfMonth()))

    private fun dateOf(millis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
}
