package com.mehul.inkless.ml

import com.google.common.truth.Truth.assertThat
import com.mehul.inkless.data.db.entity.Direction
import com.mehul.inkless.data.db.entity.TransactionType
import com.mehul.inkless.demo.DemoDataGenerator
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The evaluation harness: replays the demo's three months of spending through the model and
 * reports how often it was right. The numbers it prints are the ones in the README.
 */
class EvaluationTest {

    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")
    private val now = ZonedDateTime.of(2026, 11, 7, 20, 0, 0, 0, zone)

    private val payments = DemoDataGenerator().generate(now).transactions
        .filter { it.type == TransactionType.EXPENSE && it.direction == Direction.DEBIT && it.categoryId != null }
        .map { LabelledPayment(it.vpa, it.payeeRaw, it.amountPaise, it.timestamp, it.categoryId!!) }

    @Test
    fun `guesses get better as payments are sorted`() {
        val replayed = Evaluation.replay(payments, zone)
        fun accuracy(slice: List<Pair<LabelledPayment, Guess?>>) =
            slice.count { (p, g) -> g?.categoryId == p.categoryId }.toFloat() / slice.size
        val quarter = replayed.size / 4
        val first = accuracy(replayed.take(quarter))
        val last = accuracy(replayed.takeLast(quarter))
        println("Replay over ${replayed.size} demo payments: first quarter ${pct(first)}, last quarter ${pct(last)}")
        assertThat(last).isGreaterThan(first)
        assertThat(last).isAtLeast(0.9f)
    }

    @Test
    fun `guesses are grouped by monday-to-sunday weeks`() {
        val monday = LocalDate.of(2026, 11, 2)
        val ms = { d: LocalDate -> d.atStartOfDay(zone).toInstant().toEpochMilli() }
        val weeks = Evaluation.weekly(
            listOf(Scored(ms(monday), true), Scored(ms(monday.plusDays(6)), false), Scored(ms(monday.minusDays(1)), true)),
            today = LocalDate.of(2026, 11, 7),
            weeks = 3,
            zone = zone,
        )
        assertThat(weeks.map { it.guesses }).containsExactly(0, 1, 2).inOrder()
        assertThat(weeks.last().rate).isEqualTo(0.5f)
        assertThat(weeks.first().rate).isNull()
    }

    private fun pct(f: Float) = "${(f * 100).toInt()}%"
}
