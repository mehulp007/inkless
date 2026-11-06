package com.mehul.inkless.parsing

/**
 * Field extractors shared by every parser. Each one returns null rather than guessing, so a
 * bank-specific parser can fill in what the generic patterns miss.
 */
object Fields {

    private val ignoreCase = setOf(RegexOption.IGNORE_CASE)

    // Rs-prefixed amount, but not one that's part of "Avl Bal Rs 3,000" or a credit limit.
    private val currencyAmount = Regex("""(?<![a-z])(?:rs\.?|inr|₹)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", ignoreCase)
    private val balanceBefore = Regex("""(?:bal|balance|limit)[^0-9]{0,15}$""", ignoreCase)

    // SBI writes "debited by 45.0" with no currency at all.
    private val bareVerbAmount = Regex(
        """\b(?:debited|credited|sent|received|paid)\s+(?:by|for|with|of)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)\b""",
        ignoreCase,
    )

    private val balance = Regex(
        """(?:avl\.?|avail(?:able)?\.?|clear|total)\s*(?:bal(?:ance)?\.?|bal)\s*(?:is|:|-)?\s*(?:rs\.?|inr|₹)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
        ignoreCase,
    )

    private val account = Regex(
        """\b(?:a/?c|acct|account|card|ac)\b\.?\s*(?:no\.?\s*)?(?:ending\s*(?:with\s*)?)?[x*.\s]*(\d{3,4})\b""",
        ignoreCase,
    )

    // A handle with no dot after the @, so "me@gmail.com" isn't taken for one.
    private val vpa = Regex("""\b([a-z0-9][a-z0-9._-]{1,255}@[a-z][a-z0-9]{1,63})\b(?!\.[a-z])""", ignoreCase)

    private val labelledRef = listOf(
        Regex("""upi\s*(?:ref(?:erence)?|txn)\s*(?:id|no\.?|number)?\s*[:.#-]?\s*(\d{12})\b""", ignoreCase),
        Regex("""\b(?:ref(?:erence)?\s*(?:no\.?|number|id)?|refno|rrn|utr(?:\s*no\.?)?|txn\s*id)\s*[:.#-]?\s*(\d{12})\b""", ignoreCase),
        Regex("""\bupi[:/](?:p2[am]/|[a-z]{2,6}/)?(\d{12})\b""", ignoreCase),
    )

    private val refundWords = Regex("""\b(?:refund(?:ed)?|reversal|reversed|revert(?:ed)?)\b""", ignoreCase)

    private val debitWords = Regex(
        """\b(?:debited|sent|paid|spent|withdrawn|transferred|deducted|charged|purchase|dr)\b""",
        ignoreCase,
    )
    private val creditWords = Regex("""\b(?:credited|received|deposited|refunded|reversed|cr)\b""", ignoreCase)

    fun amountPaise(body: String): Long? {
        for (m in currencyAmount.findAll(body)) {
            if (balanceBefore.containsMatchIn(body.substring(0, m.range.first))) continue
            Amounts.toPaise(m.groupValues[1])?.takeIf { it > 0 }?.let { return it }
        }
        return bareVerbAmount.find(body)?.groupValues?.get(1)?.let(Amounts::toPaise)?.takeIf { it > 0 }
    }

    fun balancePaise(body: String): Long? =
        balance.find(body)?.groupValues?.get(1)?.let(Amounts::toPaise)

    /** Whichever of "debited"/"credited" comes first decides: "Rs 45 debited ... credited to X" is a debit. */
    fun direction(body: String): TxnDirection? {
        val debit = debitWords.find(body)?.range?.first
        val credit = creditWords.find(body)?.range?.first
        return when {
            debit == null && credit == null -> null
            credit == null -> TxnDirection.DEBIT
            debit == null -> TxnDirection.CREDIT
            debit < credit -> TxnDirection.DEBIT
            else -> TxnDirection.CREDIT
        }
    }

    fun accountLast4(body: String): String? = account.find(body)?.groupValues?.get(1)

    fun vpa(body: String): String? = vpa.find(body)?.groupValues?.get(1)?.lowercase()

    fun upiRef(body: String): String? =
        labelledRef.firstNotNullOfOrNull { it.find(body)?.groupValues?.get(1) }

    fun isRefund(body: String): Boolean = refundWords.containsMatchIn(body)

    /** Tidies a captured name: "SHARMA  CANTEEN ." -> "SHARMA CANTEEN". Null if nothing name-like is left. */
    fun cleanName(raw: String?): String? {
        val name = raw
            ?.replace(Regex("""\s+"""), " ")
            ?.trim(' ', '.', ',', ';', ':', '-', '(', ')', '/')
            ?: return null
        if (name.length !in 2..40) return null
        if (name.none(Char::isLetter)) return null
        if (name.contains('@')) return null
        return name
    }
}
