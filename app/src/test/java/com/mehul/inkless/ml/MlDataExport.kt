package com.mehul.inkless.ml

import com.mehul.inkless.data.db.entity.Direction
import com.mehul.inkless.data.db.entity.TransactionType
import com.mehul.inkless.demo.DemoDataGenerator
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Writes the data for the `ml/` notebook: five months of demo payments, and how the app's Naive
 * Bayes does on the same train/test split the notebook uses (first 75% by time to learn, the
 * rest to test). Only runs when asked: `INKLESS_ML_EXPORT=1 ./gradlew :app:testDebugUnitTest --tests "*MlDataExport*"`.
 */
class MlDataExport {

    private val zone = ZoneId.of("Asia/Kolkata")

    @Test
    fun export() {
        assumeTrue(System.getenv("INKLESS_ML_EXPORT") != null)
        val now = ZonedDateTime.of(2026, 11, 7, 20, 0, 0, 0, zone)
        val payments = DemoDataGenerator().generate(now, monthsBack = 5).transactions
            .filter { it.type == TransactionType.EXPENSE && it.direction == Direction.DEBIT && it.categoryId != null }
            .sortedBy { it.timestamp }
            .map { LabelledPayment(it.vpa, it.payeeRaw, it.amountPaise, it.timestamp, it.categoryId!!) }

        val dir = File("../ml/data").apply { mkdirs() }
        File(dir, "demo_payments.csv").printWriter().use { out ->
            out.println("timestamp,vpa,payee,amount_paise,category")
            payments.forEach { p ->
                val time = Instant.ofEpochMilli(p.timestamp).atZone(zone).toLocalDateTime()
                out.println("$time,${p.vpa.orEmpty()},${p.payee.orEmpty()},${p.amountPaise},${p.categoryId}")
            }
        }

        val cut = payments.size * 3 / 4
        val model = NaiveBayesCategorizer()
        payments.take(cut).forEach { model.learn(Evaluation.features(it, zone), it.categoryId) }
        val test = payments.drop(cut)
        val right = test.count { model.predict(Evaluation.features(it, zone)).firstOrNull()?.categoryId == it.categoryId }
        val replay = Evaluation.replay(payments, zone)
        val replayRight = replay.count { (p, g) -> g?.categoryId == p.categoryId }
        File(dir, "naive_bayes.json").writeText(
            """
            {
              "payments": ${payments.size},
              "holdout_test_size": ${test.size},
              "holdout_accuracy": ${right.toDouble() / test.size},
              "replay_accuracy": ${replayRight.toDouble() / replay.size}
            }
            """.trimIndent() + "\n",
        )
    }
}
