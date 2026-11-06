package com.mehul.inkless.ml

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** A payment with the category it really belongs to, for training and for measuring the model. */
data class LabelledPayment(
    val vpa: String?,
    val payee: String?,
    val amountPaise: Long,
    val timestamp: Long,
    val categoryId: String,
)

/** When to let the model file a payment on its own. */
object ModelTrust {
    /** Below this many payments learned, it only suggests. */
    const val MIN_EXAMPLES = 20

    /** A category it has seen fewer times than this is never filed automatically. */
    const val MIN_PER_CATEGORY = 3

    fun canAutoFile(model: NaiveBayesCategorizer, guess: Guess): Boolean =
        model.size >= MIN_EXAMPLES && model.examplesOf(guess.categoryId) >= MIN_PER_CATEGORY
}

/** One guess and whether it turned out right. */
data class Scored(val timestamp: Long, val right: Boolean)

/** A week's guesses: how many, and how many were right. */
data class WeekAccuracy(val weekStart: LocalDate, val guesses: Int, val right: Int) {
    val rate: Float? get() = if (guesses == 0) null else right.toFloat() / guesses
}

object Evaluation {

    fun features(p: LabelledPayment, zone: ZoneId) = FeatureExtractor.of(p.vpa, p.payee, p.amountPaise, p.timestamp, zone)

    /**
     * Replays payments in order the way the app sees them: guess first, then learn the real
     * category, as if the user corrected every wrong guess. The first guesses are poor; the
     * question is how fast they get better.
     */
    fun replay(payments: List<LabelledPayment>, zone: ZoneId): List<Pair<LabelledPayment, Guess?>> {
        val model = NaiveBayesCategorizer()
        return payments.sortedBy { it.timestamp }.map { p ->
            val f = features(p, zone)
            val guess = model.predict(f).firstOrNull()
            model.learn(f, p.categoryId)
            p to guess
        }
    }

    /** Guesses grouped into weeks (Monday to Sunday), the last [weeks] ending with the one holding [today]. */
    fun weekly(scored: List<Scored>, today: LocalDate, weeks: Int, zone: ZoneId): List<WeekAccuracy> {
        val thisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val byWeek = scored.groupBy {
            Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        }
        return (weeks - 1 downTo 0).map { back ->
            val start = thisWeek.minusWeeks(back.toLong())
            val week = byWeek[start].orEmpty()
            WeekAccuracy(start, week.size, week.count { it.right })
        }
    }
}
