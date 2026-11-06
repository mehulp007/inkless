package com.mehul.inkless.ml

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class NaiveBayesCategorizerTest {

    private val zone: ZoneId = ZoneOffset.UTC

    private fun at(hour: Int, day: Int = 4) = LocalDateTime.of(2026, 11, day, hour, 15).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun f(payee: String?, rupees: Long, hour: Int, vpa: String? = null, day: Int = 4) =
        FeatureExtractor.of(vpa, payee, rupees * 100, at(hour, day), zone)

    @Test
    fun `an unseen canteen is recognised from the word and the amount`() {
        val model = NaiveBayesCategorizer()
        repeat(3) {
            model.learn(f("SHARMA CANTEEN", 40, 9), "food_canteen")
            model.learn(f("Night Canteen", 60, 23), "food_canteen")
            model.learn(f("Xerox Point", 10, 11), "college")
            model.learn(f("RAPIDO", 120, 18), "travel")
        }
        val guess = model.predict(f("BLOCK C CANTEEN", 35, 10)).first()
        assertThat(guess.categoryId).isEqualTo("food_canteen")
    }

    @Test
    fun `a correction changes the next guess`() {
        val model = NaiveBayesCategorizer()
        repeat(4) { model.learn(f("STORE", 150, 13), "shopping") }
        assertThat(model.predict(f("RAMU STORE", 30, 17)).first().categoryId).isEqualTo("shopping")
        // The user says Ramu's store is where they get chai, a few times.
        repeat(3) { model.learn(f("RAMU STORE", 30, 17), "food_canteen") }
        assertThat(model.predict(f("RAMU STORE", 30, 17)).first().categoryId).isEqualTo("food_canteen")
    }

    @Test
    fun `a long name is no surer than a short one`() {
        val model = NaiveBayesCategorizer()
        repeat(5) {
            model.learn(f("SHARMA CANTEEN", 40, 9), "food_canteen")
            model.learn(f("RAPIDO", 120, 18), "travel")
        }
        // An amount and time it hasn't seen, so only the name speaks.
        val short = model.predict(f("CANTEEN", 700, 14)).first()
        val long = model.predict(f("SHARMA CANTEEN CANTEEN SHARMA CANTEEN SHARMA CANTEEN", 700, 14)).first()
        assertThat(short.categoryId).isEqualTo("food_canteen")
        assertThat(long.categoryId).isEqualTo("food_canteen")
        assertThat(long.confidence).isWithin(0.05f).of(short.confidence)
    }

    @Test
    fun `confidences add up to one and nothing is guessed before it has learned`() {
        val model = NaiveBayesCategorizer()
        assertThat(model.predict(f("ANY", 10, 10))).isEmpty()
        model.learn(f("A", 10, 10), "x")
        model.learn(f("B", 500, 20), "y")
        assertThat(model.predict(f("C", 50, 12)).sumOf { it.confidence.toDouble() }).isWithin(1e-4).of(1.0)
    }

    @Test
    fun `stored counts bring back the same model`() {
        val model = NaiveBayesCategorizer()
        model.learn(f("SHARMA CANTEEN", 40, 9), "food_canteen")
        model.learn(f("RAPIDO", 120, 18, vpa = "rapido@ybl"), "travel")
        val back = NaiveBayesCategorizer.fromCounts(model.counts())
        val probe = f("CANTEEN", 30, 9)
        assertThat(back.predict(probe)).isEqualTo(model.predict(probe))
        assertThat(back.size).isEqualTo(2)
    }

    @Test
    fun `opaque qr handles look alike and amounts fall in ranges`() {
        val a = FeatureExtractor.of("q829184562@ybl", null, 2000, at(17), zone)
        val b = FeatureExtractor.of("q551203987@ybl", null, 2500, at(16), zone)
        assertThat(a.text).isEqualTo(b.text)
        assertThat(a.context.drop(1)).isEqualTo(b.context.drop(1)) // all but the merchant itself
        assertThat(FeatureExtractor.amountBucket(29_99)).isEqualTo(0)
        assertThat(FeatureExtractor.amountBucket(30_00)).isEqualTo(1)
        assertThat(FeatureExtractor.amountBucket(10_000_00)).isEqualTo(8)
    }

    @Test
    fun `only a model that has learned enough may file by itself`() {
        val model = NaiveBayesCategorizer()
        repeat(2) { model.learn(f("RAPIDO", 120, 18), "travel") }
        repeat(18) { model.learn(f("CANTEEN", 40, 9), "food_canteen") }
        assertThat(ModelTrust.canAutoFile(model, Guess("food_canteen", 0.9f))).isTrue()
        // Seen only twice: suggest, don't file.
        assertThat(ModelTrust.canAutoFile(model, Guess("travel", 0.9f))).isFalse()
    }
}
