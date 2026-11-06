package com.mehul.inkless.parsing

/**
 * Bank alerts write amounts as "Rs.1,234.50", "INR 45", "Rs 1234.5" or "₹2,10,000.00".
 * The app only ever stores paise as a Long, so these helpers never go through Double.
 */
object Amounts {

    private val amountPattern = Regex(
        """(?:rs\.?|inr|₹)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE,
    )

    /** First amount in [text] that carries a currency marker, in paise. */
    fun findPaise(text: String): Long? =
        amountPattern.find(text)?.groupValues?.get(1)?.let(::toPaise)

    /** "1,234.5" -> 123450. Returns null for anything that isn't a plain rupee amount. */
    fun toPaise(raw: String): Long? {
        val cleaned = raw.replace(",", "").trim()
        if (cleaned.isEmpty() || cleaned.count { it == '.' } > 1) return null

        val rupeePart = cleaned.substringBefore('.')
        val paisePart = cleaned.substringAfter('.', missingDelimiterValue = "")
        if (rupeePart.isEmpty() || !rupeePart.all(Char::isDigit)) return null
        if (paisePart.length > 2 || !paisePart.all(Char::isDigit)) return null

        val rupees = rupeePart.toLongOrNull() ?: return null
        return rupees * 100 + paisePart.padEnd(2, '0').toLong()
    }
}
