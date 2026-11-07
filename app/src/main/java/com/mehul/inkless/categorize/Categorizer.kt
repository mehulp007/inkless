package com.mehul.inkless.categorize

import com.mehul.inkless.ml.Guess

enum class RuleField { UPI_ID, PAYEE, EITHER }

enum class RuleMatch { CONTAINS, EXACT, REGEX }

/** A user rule, e.g. "UPI ID contains canteen → Food & Canteen". Lower priority runs first. */
data class CategoryRule(
    val field: RuleField,
    val match: RuleMatch,
    val pattern: String,
    val categoryId: String,
    val priority: Int,
)

enum class DecisionSource { RULE, MERCHANT_MEMORY, MODEL, KEYWORD }

data class Decision(val categoryId: String, val source: DecisionSource, val confidence: Float)

/**
 * Picks a category for a payment, most specific knowledge first: the user's own rules, then what
 * they chose for this merchant before, then the learned model when it's sure enough, then the
 * built-in keyword list. Null means nobody knows and the payment goes to the review inbox.
 */
object Categorizer {

    /** Auto-file at or above this; below it the payment waits for review. */
    const val AUTO_FILE_THRESHOLD = 0.8f

    fun decide(
        vpa: String?,
        payee: String?,
        rules: List<CategoryRule>,
        merchantCategoryId: String?,
        /** The model's best guess, only when it has learned enough to be trusted with filing. */
        model: Guess? = null,
    ): Decision? {
        rules.sortedBy { it.priority }.firstOrNull { it.matches(vpa, payee) }?.let {
            return Decision(it.categoryId, DecisionSource.RULE, 1f)
        }
        merchantCategoryId?.let { return Decision(it, DecisionSource.MERCHANT_MEMORY, 1f) }
        model?.takeIf { it.confidence >= AUTO_FILE_THRESHOLD }?.let { return Decision(it.categoryId, DecisionSource.MODEL, it.confidence) }
        Keywords.categoryFor(vpa, payee)?.let { return Decision(it, DecisionSource.KEYWORD, Keywords.CONFIDENCE) }
        return null
    }

    /** Whether [this] rule picks a payment to or from [vpa] / [payee]. */
    fun CategoryRule.matches(vpa: String?, payee: String?): Boolean {
        val candidates = when (field) {
            RuleField.UPI_ID -> listOfNotNull(vpa)
            RuleField.PAYEE -> listOfNotNull(payee)
            RuleField.EITHER -> listOfNotNull(vpa, payee)
        }
        val wanted = pattern.trim()
        if (wanted.isEmpty()) return false
        return candidates.any { value ->
            when (match) {
                RuleMatch.CONTAINS -> value.contains(wanted, ignoreCase = true)
                RuleMatch.EXACT -> value.equals(wanted, ignoreCase = true)
                // A broken pattern typed in the editor just never matches.
                RuleMatch.REGEX -> runCatching { Regex(wanted, RegexOption.IGNORE_CASE).containsMatchIn(value) }.getOrDefault(false)
            }
        }
    }

    /** How many of [payees] (UPI ID, name) [rule] would pick: the live preview in the rule editor. */
    fun countMatches(rule: CategoryRule, payees: List<Pair<String?, String?>>): Int =
        payees.count { (vpa, payee) -> rule.matches(vpa, payee) }
}
