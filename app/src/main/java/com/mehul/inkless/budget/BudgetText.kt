package com.mehul.inkless.budget

import com.mehul.inkless.ui.format.Money
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** The sentences the budget is told in, on Home, the widget and the weekly summary. */
object BudgetText {

    private val monthFormat = DateTimeFormatter.ofPattern("MMM")
    private val dayMonth = DateTimeFormatter.ofPattern("d MMM")

    /** Rounded to the rupee, for amounts that are estimates anyway: "₹450". */
    private fun rupees(paise: Long) = Money.format((paise + 50) / 100 * 100)

    /** "₹300 spent today" */
    fun spentToday(snapshot: BudgetSnapshot): String = "${Money.format(snapshot.spentTodayPaise.coerceAtLeast(0))} spent today"

    /** "₹2,700 / ₹12,000" */
    fun spentSlashBudget(snapshot: BudgetSnapshot): String =
        "${Money.format(snapshot.spentPaise.coerceAtLeast(0))} / ${Money.format(snapshot.budgetPaise)}"

    /** "spent · allowance, 1–30 Nov" */
    fun cycleLine(snapshot: BudgetSnapshot): String {
        val start = snapshot.cycle.start
        val last = snapshot.cycle.endExclusive.minusDays(1)
        val range = if (start.month == last.month) "${start.dayOfMonth}–${last.format(dayMonth)}" else "${start.format(dayMonth)}–${last.format(dayMonth)}"
        return "spent · allowance, $range"
    }

    /** "₹9,300 left · 25 days including today" */
    fun leftLine(snapshot: BudgetSnapshot): String {
        val daysLeft = snapshot.cycle.daysLeft(snapshot.today)
        val days = if (daysLeft == 1) "last day" else "$daysLeft days including today"
        val remaining = snapshot.remainingPaise
        return if (remaining >= 0) "${Money.format(remaining)} left · $days" else "${Money.format(-remaining)} over · $days"
    }

    /** The widget's short version in whole rupees: "₹9,300 left · 25 days", "₹500 over · last day". */
    fun widgetLine(snapshot: BudgetSnapshot): String {
        val daysLeft = snapshot.cycle.daysLeft(snapshot.today)
        val days = if (daysLeft == 1) "last day" else "$daysLeft days"
        val remaining = snapshot.remainingPaise
        // Rounded the careful way, like the safe-to-spend amount: never more left than there is.
        return if (remaining >= 0) {
            "${Money.format(remaining / 100 * 100)} left · $days"
        } else {
            "${Money.format((-remaining + 99) / 100 * 100)} over · $days"
        }
    }

    /** "22.5%", or "40%" when there's nothing after the point. */
    fun usedPercent(snapshot: BudgetSnapshot): String {
        val tenths = kotlin.math.round(snapshot.usedFraction * 1000).toInt()
        return if (tenths % 10 == 0) "${tenths / 10}%" else "${tenths / 10}.${tenths % 10}%"
    }

    /** "At ₹450/day, your money lasts until 27 Nov." */
    fun paceLine(snapshot: BudgetSnapshot): String {
        val pace = rupees(snapshot.pacePaisePerDay)
        return when (val runway = snapshot.runway) {
            Runway.Spent -> "This month's allowance is used up."
            Runway.Lasts ->
                if (snapshot.pacePaisePerDay <= 0) "Nothing spent yet this month."
                else "At $pace/day, your money lasts until your allowance on ${snapshot.cycle.endExclusive.format(dayMonth)}."
            is Runway.RunsOut -> "At $pace/day, your money lasts until ${runway.date.format(dayMonth)}."
        }
    }

    private val dayMonthYear = DateTimeFormatter.ofPattern("d MMM yyyy")

    /** "1–30 Nov 2026" */
    fun cycleRange(snapshot: BudgetSnapshot): String {
        val start = snapshot.cycle.start
        val last = snapshot.cycle.endExclusive.minusDays(1)
        return if (start.month == last.month) "${start.dayOfMonth}–${last.format(dayMonthYear)}" else "${start.format(dayMonth)} – ${last.format(dayMonthYear)}"
    }

    /** "left of your ₹12,000 allowance · 22.5% used" */
    fun leftOfAllowance(snapshot: BudgetSnapshot): String =
        "${if (snapshot.remainingPaise >= 0) "left of" else "over"} your ${Money.format(snapshot.budgetPaise)} allowance · ${usedPercent(snapshot)} used"

    /** "₹2,700 net spending" */
    fun netSpending(snapshot: BudgetSnapshot): String = "${Money.format(snapshot.spentPaise)} net spending"

    /** "Next arrival · 1 Dec" */
    fun nextArrival(snapshot: BudgetSnapshot): String = "Next arrival · ${snapshot.cycle.endExclusive.format(dayMonth)}"

    /** "6 Nov · ₹2,700": the label on today's point of the chart. */
    fun todayTag(snapshot: BudgetSnapshot): String = "${snapshot.today.format(dayMonth)} · ${Money.format(snapshot.spentPaise)}"

    /** "₹450/day so far vs ₹400/day planned." */
    fun paceVsPlan(snapshot: BudgetSnapshot): String {
        val planned = snapshot.budgetPaise / snapshot.cycle.days.coerceAtLeast(1)
        return "${rupees(snapshot.pacePaisePerDay)}/day so far vs ${rupees(planned)}/day planned."
    }

    /** "At this pace, the allowance runs out on 27 Nov." */
    fun runsOutLine(snapshot: BudgetSnapshot): String = when (val runway = snapshot.runway) {
        Runway.Lasts -> "At this pace, it lasts until the next allowance."
        is Runway.RunsOut -> "At this pace, the allowance runs out on ${runway.date.format(dayMonth)}."
        Runway.Spent -> "This month's allowance is used up."
    }

    /** "₹9,600 ÷ 25 days − ₹300 = ₹84": how today's safe-to-spend comes about. */
    fun formula(snapshot: BudgetSnapshot): String {
        val leftThisMorning = snapshot.budgetPaise - (snapshot.spentPaise - snapshot.spentTodayPaise)
        val days = snapshot.cycle.daysLeft(snapshot.today)
        return "${Money.format(leftThisMorning)} ÷ $days ${if (days == 1) "day" else "days"} − " +
            "${Money.format(snapshot.spentTodayPaise.coerceAtLeast(0))} = ${safeToSpendAmount(snapshot)}"
    }

    /** "About ₹380/day keeps you on track for 1 Dec." Only when the money would run out first. */
    fun onTrackLine(snapshot: BudgetSnapshot): String? {
        if (snapshot.runway !is Runway.RunsOut) return null
        val perDay = snapshot.remainingPaise / snapshot.cycle.daysLeft(snapshot.today)
        return "About ${rupees(perDay)}/day keeps you on track for ${snapshot.cycle.endExclusive.format(dayMonth)}."
    }

    fun safeToSpendLabel(snapshot: BudgetSnapshot): String =
        if (snapshot.safeToSpendPaise >= 0) "safe to spend today" else "over today's share"

    /** In whole rupees, rounded down while it's what's left and up once it's over: never more than is really there. */
    fun safeToSpendAmount(snapshot: BudgetSnapshot): String {
        val safe = snapshot.safeToSpendPaise
        return if (safe >= 0) Money.format(safe / 100 * 100) else Money.format((-safe + 99) / 100 * 100)
    }

    fun runway(snapshot: BudgetSnapshot): String = when (val runway = snapshot.runway) {
        Runway.Lasts -> "At this pace it lasts until your allowance on the ${ordinal(snapshot.cycle.endExclusive)}."
        is Runway.RunsOut -> "At this pace you'll run out on the ${ordinal(runway.date)}" +
            (if (runway.date.month != snapshot.today.month) " ${runway.date.format(monthFormat)}" else "") + "."
        Runway.Spent -> "This month's allowance is used up."
    }

    fun spentOfBudget(snapshot: BudgetSnapshot): String {
        val daysLeft = snapshot.cycle.daysLeft(snapshot.today)
        return "${Money.format(snapshot.spentPaise.coerceAtLeast(0))} of ${Money.format(snapshot.budgetPaise)} spent · " +
            if (daysLeft == 1) "allowance tomorrow" else "$daysLeft days to allowance"
    }

    /** Null when no food cap is set. */
    fun food(snapshot: BudgetSnapshot): String? {
        val cap = snapshot.dailyFoodCapPaise ?: return null
        val skipped = snapshot.daysOverFoodCap
        return "Food today ${Money.format(snapshot.foodTodayPaise.coerceAtLeast(0))} of ${Money.format(cap)}" +
            when (skipped) {
                0 -> ""
                1 -> " · over on 1 day this month"
                else -> " · over on $skipped days this month"
            }
    }

    fun ordinal(date: LocalDate): String {
        val day = date.dayOfMonth
        val suffix = if (day in 11..13) "th" else when (day % 10) {
            1 -> "st"
            2 -> "nd"
            3 -> "rd"
            else -> "th"
        }
        return "$day$suffix"
    }
}
