package com.mehul.inkless.insights

import com.mehul.inkless.data.db.dao.GuessOutcome
import com.mehul.inkless.ml.Evaluation
import com.mehul.inkless.ml.Scored
import com.mehul.inkless.ml.WeekAccuracy
import java.time.LocalDate
import java.time.ZoneId

/**
 * How well Inkless guesses, two ways:
 * - the model's own guess at each payment against the category it ended up with, week by week
 *   (this is the line that should climb as the user corrects it);
 * - how often a payment Inkless filed by itself was changed by the user (the override rate).
 */
data class AccuracyReport(
    val weeks: List<WeekAccuracy>,
    val guesses: Int,
    val right: Int,
    /** Right out of the last [RECENT] guesses. */
    val recentRight: Int,
    val recentGuesses: Int,
    val filed: Int,
    val overridden: Int,
    val learnedFrom: Int,
) {
    val recentRate: Float? get() = if (recentGuesses == 0) null else recentRight.toFloat() / recentGuesses
    val overrideRate: Float? get() = if (filed == 0) null else overridden.toFloat() / filed

    companion object {
        const val RECENT = 30
        const val WEEKS = 12

        fun of(outcomes: List<GuessOutcome>, learnedFrom: Int, today: LocalDate, zone: ZoneId): AccuracyReport {
            val scored = outcomes.filter { it.modelCategoryId != null }
                .map { Scored(it.createdAt, it.modelCategoryId == it.finalCategoryId) }
                .sortedBy { it.timestamp }
            val recent = scored.takeLast(RECENT)
            val filed = outcomes.filter { it.filedCategoryId != null }
            return AccuracyReport(
                weeks = Evaluation.weekly(scored, today, WEEKS, zone),
                guesses = scored.size,
                right = scored.count { it.right },
                recentRight = recent.count { it.right },
                recentGuesses = recent.size,
                filed = filed.size,
                overridden = filed.count { it.filedCategoryId != it.finalCategoryId },
                learnedFrom = learnedFrom,
            )
        }
    }
}
