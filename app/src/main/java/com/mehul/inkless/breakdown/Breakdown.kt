package com.mehul.inkless.breakdown

/** One line of a breakdown being edited, before it's saved. */
data class DraftItem(
    val amountPaise: Long,
    val categoryId: String?,
    val note: String? = null,
)

/**
 * The rules of breaking one payment down into items. The bank only ever says "₹5,000 to Amazon",
 * so the user splits that total into parts with their own categories, and the parts must add up
 * to the payment to the paisa.
 */
object Breakdown {

    /** What's left for the last item once the others are taken off the payment. */
    fun rest(totalPaise: Long, others: List<DraftItem>): Long = totalPaise - others.sumOf { it.amountPaise }

    /**
     * Why [others] plus a last item taking the rest isn't a breakdown yet, or null when it is.
     * [lastCategoryId] is the category picked for that last item.
     */
    fun problem(totalPaise: Long, others: List<DraftItem>, lastCategoryId: String?): String? {
        val rest = rest(totalPaise, others)
        return when {
            others.isEmpty() -> "Add at least two items"
            others.any { it.amountPaise <= 0 } -> "Every item needs an amount"
            rest < 0 -> "The items add up to more than the payment"
            rest == 0L -> "Nothing is left for the last item"
            others.any { it.categoryId == null } || lastCategoryId == null -> "Pick a category for every item"
            else -> null
        }
    }

    /** The items to save: [others] plus a last item taking the rest. Null if that isn't valid. */
    fun complete(totalPaise: Long, others: List<DraftItem>, lastCategoryId: String?, lastNote: String?): List<DraftItem>? {
        if (problem(totalPaise, others, lastCategoryId) != null) return null
        return others + DraftItem(rest(totalPaise, others), lastCategoryId, lastNote)
    }

    /** Whether saved items are a proper breakdown of a payment of [totalPaise]. */
    fun isValid(totalPaise: Long, amounts: List<Long>): Boolean =
        amounts.size >= 2 && amounts.all { it > 0 } && amounts.sum() == totalPaise

    /**
     * The item a payment is listed under on Home: the biggest one, or the first added when two are
     * equally big. Items are expected in the order they were added.
     */
    fun <T> primary(items: List<T>, amountOf: (T) -> Long): T? = items.maxByOrNull(amountOf)

    /**
     * Where money back from the merchant goes when the original payment was broken down: an item
     * of exactly that amount says which category it was for. No such item, or matching items in
     * different categories, and it's not clear, so null (the user is asked).
     */
    fun refundCategory(refundPaise: Long, items: List<DraftItem>): String? =
        items.filter { it.amountPaise == refundPaise }
            .mapNotNull { it.categoryId }
            .distinct()
            .singleOrNull()
}
