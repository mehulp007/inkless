package com.mehul.inkless.budget

import com.mehul.inkless.data.db.SpendPart
import com.mehul.inkless.data.db.entity.CategoryEntity
import com.mehul.inkless.ui.format.Money

/** The Sunday-evening summary: the week's spending, where it went, and what's left. */
object WeeklyDigest {

    data class Message(val title: String, val text: String)

    /** Null for a week with no spending: nothing worth a notification. */
    fun compose(weekParts: List<SpendPart>, categories: List<CategoryEntity>, budget: BudgetSnapshot?): Message? {
        val spent = weekParts.sumOf { it.amountPaise }
        if (spent <= 0) return null
        val names = categories.associateBy { it.id }
        val top = weekParts.filter { it.categoryId != null }
            .groupBy { it.categoryId!! }
            .mapValues { (_, parts) -> parts.sumOf { it.amountPaise } }
            .filterValues { it > 0 }
            .entries.sortedByDescending { it.value }
            .take(TOP_CATEGORIES)
            .joinToString(" · ") { (id, paise) -> "${names[id]?.let { "${it.emoji} ${it.name}" } ?: "Other"} ${Money.format(paise)}" }
        val left = budget?.let {
            if (it.remainingPaise > 0) {
                "${Money.format(it.remainingPaise)} left until your allowance on the ${BudgetText.ordinal(it.cycle.endExclusive)}."
            } else {
                "This month's allowance is used up."
            }
        }
        return Message(
            title = "This week: ${Money.format(spent)} spent",
            text = listOfNotNull(top.ifEmpty { null }, left).joinToString("\n"),
        )
    }

    private const val TOP_CATEGORIES = 3
}
