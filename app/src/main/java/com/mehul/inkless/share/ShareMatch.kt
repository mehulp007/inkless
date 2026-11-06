package com.mehul.inkless.share

import com.mehul.inkless.breakdown.DraftItem
import com.mehul.inkless.categorize.Keywords
import com.mehul.inkless.data.db.entity.TransactionEntity
import com.mehul.inkless.data.seed.CategoryIds
import com.mehul.inkless.parsing.ParsedOrder

/** Turning a shared order into a draft breakdown of one of the user's payments. */
object ShareMatch {

    /**
     * The payments it could be, likeliest first: ones for exactly the order's total (or, if the
     * page gave none, the items' sum), then the rest, newest first within each.
     */
    fun rank(payments: List<TransactionEntity>, order: ParsedOrder): List<TransactionEntity> {
        val amount = amountOf(order)
        return payments.sortedWith(compareBy<TransactionEntity> { it.amountPaise != amount }.thenByDescending { it.timestamp })
    }

    /** The payment to preselect: only when exactly one has the order's amount. */
    fun bestMatch(payments: List<TransactionEntity>, order: ParsedOrder): TransactionEntity? {
        val amount = amountOf(order) ?: return null
        return payments.filter { it.amountPaise == amount }.singleOrNull()
    }

    /** Each item as a draft line named after it, with a guessed category where its name gives one away. */
    fun draft(order: ParsedOrder): List<DraftItem> =
        order.items.map { DraftItem(it.amountPaise, guessCategory(it.name), it.name) }

    private fun amountOf(order: ParsedOrder): Long? =
        order.totalPaise ?: order.items.sumOf { it.amountPaise }.takeIf { it > 0 }

    private val nonWord = Regex("[^a-z0-9]+")

    // Words that give away what a bought thing is. Merchant keywords cover shops; these cover goods.
    private val itemWords: List<Pair<String, String>> = buildList {
        fun add(category: String, vararg words: String) = words.forEach { add(it to category) }
        add(CategoryIds.COLLEGE, "notebook", "notebooks", "pen", "pens", "pencil", "register", "calculator", "stapler", "highlighter", "textbook", "guide", "fevicol", "geometry", "classmate")
        add(CategoryIds.GROCERIES, "milk", "bread", "atta", "rice", "dal", "oil", "sugar", "salt", "eggs", "butter", "paneer", "curd", "noodles", "maggi", "biscuits", "soap", "shampoo", "detergent", "toothpaste", "onion", "tomato", "potato", "fruits", "vegetables")
        add(CategoryIds.SHOPPING, "shirt", "tshirt", "jeans", "trousers", "shoes", "sneakers", "slippers", "jacket", "hoodie", "kurta", "backpack", "bag", "watch", "headphones", "earphones", "earbuds", "charger", "cable", "speaker", "bottle")
        add(CategoryIds.HEALTH, "tablet", "tablets", "syrup", "medicine", "capsules", "bandage", "sanitizer", "vitamin")
        add(CategoryIds.FOOD_CANTEEN, "chocolate", "chips", "snack", "juice", "coffee", "tea", "cold", "drink")
    }

    fun guessCategory(name: String): String? {
        val words = name.lowercase().replace("-", "").split(nonWord).filter { it.isNotEmpty() }.toSet()
        return itemWords.firstOrNull { it.first in words }?.second
            ?: Keywords.categoryFor(null, name)
    }
}
