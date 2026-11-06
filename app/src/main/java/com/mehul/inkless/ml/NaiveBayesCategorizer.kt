package com.mehul.inkless.ml

import kotlin.math.exp
import kotlin.math.ln

/** A category the model thinks a payment belongs to, and how sure it is (0–1). */
data class Guess(val categoryId: String, val confidence: Float)

/** One stored count: how often [feature] was seen in payments of [categoryId]. */
data class FeatureCount(val feature: String, val categoryId: String, val count: Int)

/**
 * Multinomial Naive Bayes over [Features]: for each category, how often each feature showed up in
 * payments the user filed there. It learns one payment at a time, so every correction counts
 * straight away, and it's small enough to keep as a table of counts.
 *
 * Plain Naive Bayes multiplies the evidence of every feature, so a long payee name (dozens of
 * letter pieces) makes it absurdly sure of itself. Here the text counts as [TEXT_WEIGHT] pieces
 * of evidence however long it is, and amount, time and day as one each, which keeps the
 * confidence honest enough to use against the 0.8 auto-file threshold.
 */
class NaiveBayesCategorizer(private val textWeight: Double = TEXT_WEIGHT) {

    // feature -> category -> count
    private val counts = HashMap<String, HashMap<String, Int>>()
    // group ("w", "g", "a", …) -> category -> total count in that group
    private val groupTotals = HashMap<String, HashMap<String, Int>>()
    // group -> features seen in it
    private val vocabulary = HashMap<String, HashSet<String>>()
    // category -> payments learned
    private val examples = HashMap<String, Int>()

    /** How many payments it has learned from. */
    val size: Int get() = examples.values.sum()

    fun examplesOf(categoryId: String): Int = examples[categoryId] ?: 0

    fun learn(features: Features, categoryId: String) {
        examples.merge(categoryId, 1, Int::plus)
        (features.text + features.context).forEach { add(it, categoryId, 1) }
    }

    private fun add(feature: String, categoryId: String, by: Int) {
        counts.getOrPut(feature) { HashMap() }.merge(categoryId, by, Int::plus)
        val group = feature.substringBefore(':')
        groupTotals.getOrPut(group) { HashMap() }.merge(categoryId, by, Int::plus)
        vocabulary.getOrPut(group) { HashSet() }.add(feature)
    }

    /** Every category it knows, most likely first. Empty until it has learned something. */
    fun predict(features: Features): List<Guess> {
        if (examples.isEmpty()) return emptyList()
        val total = size.toDouble()
        // A word no payment ever had says nothing; left in, smoothing would make it point at
        // whichever category has the fewest words so far.
        val text = features.text.filter { it in counts }
        val context = features.context.filter { it in counts }
        val scores = examples.keys.associateWith { category ->
            val prior = ln(examples.getValue(category) / total)
            val fromText = if (text.isEmpty()) 0.0 else textWeight * text.sumOf { logLikelihood(it, category) } / text.size
            prior + fromText + context.sumOf { logLikelihood(it, category) }
        }
        // Softmax, shifted by the best score so exp() can't overflow.
        val best = scores.values.max()
        val weights = scores.mapValues { exp(it.value - best) }
        val sum = weights.values.sum()
        return weights.map { (category, w) -> Guess(category, (w / sum).toFloat()) }.sortedByDescending { it.confidence }
    }

    // Laplace-smoothed: a feature never seen with a category still gets a small chance.
    private fun logLikelihood(feature: String, category: String): Double {
        val group = feature.substringBefore(':')
        val count = counts[feature]?.get(category) ?: 0
        val groupTotal = groupTotals[group]?.get(category) ?: 0
        val vocab = (vocabulary[group]?.size ?: 0) + 1
        return ln((count + 1.0) / (groupTotal + vocab))
    }

    /** The counts, for storing. Each category's number of payments is stored under [EXAMPLES]. */
    fun counts(): List<FeatureCount> =
        examples.map { (category, n) -> FeatureCount(EXAMPLES, category, n) } +
            counts.flatMap { (feature, byCategory) -> byCategory.map { (category, n) -> FeatureCount(feature, category, n) } }

    companion object {
        const val TEXT_WEIGHT = 2.0
        const val EXAMPLES = "#examples"

        fun fromCounts(rows: List<FeatureCount>): NaiveBayesCategorizer = NaiveBayesCategorizer().apply {
            for (row in rows) {
                if (row.feature == EXAMPLES) examples[row.categoryId] = row.count else add(row.feature, row.categoryId, row.count)
            }
        }
    }
}
