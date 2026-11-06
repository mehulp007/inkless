package com.mehul.inkless.budget

import com.mehul.inkless.ui.format.Money
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** The sentences the budget is told in, on Home, the widget and the weekly summary. */
object BudgetText {

    private val monthFormat = DateTimeFormatter.ofPattern("MMM")

    fun safeToSpendLabel(snapshot: BudgetSnapshot): String =
        if (snapshot.safeToSpendPaise >= 0) "safe to spend today" else "over today's share"

    fun safeToSpendAmount(snapshot: BudgetSnapshot): String = Money.format(kotlin.math.abs(snapshot.safeToSpendPaise))

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
