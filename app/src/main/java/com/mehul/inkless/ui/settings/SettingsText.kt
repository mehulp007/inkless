package com.mehul.inkless.ui.settings

import com.mehul.inkless.data.db.entity.BudgetEntity
import com.mehul.inkless.data.db.entity.MyAccountEntity
import com.mehul.inkless.ui.budget.ordinal
import com.mehul.inkless.ui.format.Money

/** The short lines under each "Your ledger" link in Settings. */
data class LedgerSummary(
    val allowance: String = "",
    val categories: String = "",
    val accounts: String = "",
)

object SettingsText {

    /** "₹12,000 · arrives on the 1st". */
    fun allowance(budget: BudgetEntity?): String {
        if (budget == null || budget.amountPaise <= 0) return "Not set yet"
        val amount = Money.format(budget.amountPaise)
        return budget.allowanceDay?.let { "$amount · arrives on the ${ordinal(it)}" } ?: "$amount a month"
    }

    /** "13 categories · 2 rules". */
    fun categories(categories: Int, rules: Int): String {
        val rulePart = when (rules) {
            0 -> "no rules yet"
            1 -> "1 rule"
            else -> "$rules rules"
        }
        return "$categories categories · $rulePart"
    }

    /** "SBI savings · rahul@okaxis +1": what the user called them, else the ID itself. */
    fun accounts(accounts: List<MyAccountEntity>): String {
        if (accounts.isEmpty()) return "None added yet"
        val shown = accounts.take(2).joinToString(" · ") { it.label ?: it.value }
        val more = accounts.size - 2
        return if (more > 0) "$shown +$more" else shown
    }

    fun askAbove(paise: Long?): String =
        paise?.let { "After payments over ${Money.format(it)}" } ?: "Never. Break one down from its page"
}
