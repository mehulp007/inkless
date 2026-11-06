package com.mehul.inkless.ml

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

/**
 * What the model looks at in a payment. [text] comes from the payee name and UPI ID: whole words
 * plus three-letter pieces of them, so "Sharma Canteen" and "Night Canteen" share "can", "ant", …
 * [context] is one token each for the merchant itself, the amount, the time of day, the day of the
 * week, and amount and time together (₹20–₹60 at 4 pm is chai; ₹400 at 11 pm is a late-night
 * order). The merchant token lets it learn a "q829…@ybl" it has been told about, even though every
 * QR handle's words look the same.
 */
data class Features(val text: List<String>, val context: List<String>)

object FeatureExtractor {

    private val word = Regex("[a-z]+|[0-9]+")

    fun of(vpa: String?, payee: String?, amountPaise: Long, timestamp: Long, zone: ZoneId): Features {
        val words = buildList {
            payee?.let { addAll(words(it)) }
            vpa?.let { id ->
                val handle = id.substringBefore('@')
                // Opaque QR handles ("q829184562", "paytmqr2810…") say nothing but their shape.
                addAll(words(handle))
                id.substringAfter('@', "").takeIf { it.isNotEmpty() }?.let { add("@$it") }
            }
        }.distinct()
        val text = words.flatMap { w -> listOf("w:$w") + grams(w) }

        val time = Instant.ofEpochMilli(timestamp).atZone(zone)
        val amount = amountBucket(amountPaise)
        val hour = hourBand(time.hour)
        val day = if (time.dayOfWeek == DayOfWeek.SATURDAY || time.dayOfWeek == DayOfWeek.SUNDAY) "weekend" else "weekday"
        val merchant = (vpa ?: payee)?.trim()?.lowercase()
        return Features(text, listOfNotNull(merchant?.let { "m:$it" }, "a:$amount", "h:$hour", "d:$day", "x:$amount-$hour"))
    }

    /** Lower-case words; runs of digits become "#", so every "q829…" looks alike. */
    private fun words(raw: String): List<String> = word.findAll(raw.lowercase())
        .map { if (it.value[0].isDigit()) "#" else it.value }
        .toList()

    private fun grams(w: String): List<String> {
        if (w.length < 4) return emptyList()
        val padded = " $w "
        return (0..padded.length - 3).map { "g:" + padded.substring(it, it + 3) }
    }

    private val AMOUNT_EDGES = longArrayOf(30, 60, 100, 200, 400, 800, 1_500, 3_000)

    fun amountBucket(paise: Long): Int {
        val rupees = paise / 100
        return AMOUNT_EDGES.indexOfFirst { rupees < it }.let { if (it == -1) AMOUNT_EDGES.size else it }
    }

    fun hourBand(hour: Int): String = when (hour) {
        in 5..9 -> "morning"
        in 10..13 -> "midday"
        in 14..17 -> "afternoon"
        in 18..21 -> "evening"
        else -> "night"
    }
}
