package com.mehul.inkless.ui.format

import kotlin.math.absoluteValue

object Money {

    /**
     * Paise to rupees with Indian digit grouping: 12345650 -> "₹1,23,456.50".
     * Whole-rupee amounts drop the paise ("₹45", not "₹45.00") unless [alwaysShowPaise] is set.
     */
    fun format(paise: Long, alwaysShowPaise: Boolean = false): String {
        val abs = paise.absoluteValue
        val rupees = groupIndian((abs / 100).toString())
        val remainder = abs % 100
        val body = if (remainder == 0L && !alwaysShowPaise) {
            rupees
        } else {
            "$rupees.${remainder.toString().padStart(2, '0')}"
        }
        return if (paise < 0) "-₹$body" else "₹$body"
    }

    /** Compact, for chart labels: "₹950", "₹1.2k", "₹1.5L". */
    fun short(paise: Long): String {
        val rupees = paise.coerceAtLeast(0) / 100
        return when {
            rupees < 1_000 -> "₹$rupees"
            rupees < 1_00_000 -> "₹" + oneDecimal(rupees, 1_000) + "k"
            else -> "₹" + oneDecimal(rupees, 1_00_000) + "L"
        }
    }

    private fun oneDecimal(value: Long, unit: Long): String {
        val tenths = value * 10 / unit
        return if (tenths % 10 == 0L) "${tenths / 10}" else "${tenths / 10}.${tenths % 10}"
    }

    // Last three digits stay together, everything before them goes in pairs (lakh, crore).
    private fun groupIndian(digits: String): String {
        if (digits.length <= 3) return digits
        val head = digits.dropLast(3)
        val pairs = head.reversed().chunked(2).joinToString(",").reversed()
        return "$pairs,${digits.takeLast(3)}"
    }
}
