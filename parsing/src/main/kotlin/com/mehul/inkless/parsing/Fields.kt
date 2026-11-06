package com.mehul.inkless.parsing

/**
 * Field extractors shared by every parser. Each one returns null rather than guessing, so a
 * bank-specific parser can fill in what the generic patterns miss. They expect the body with its
 * whitespace collapsed (see [SmsReader]), since banks break the same alert over lines differently.
 */
object Fields {

    private val ignoreCase = setOf(RegexOption.IGNORE_CASE)

    // Rs-prefixed amount, but not one that's part of "Avl Bal Rs 3,000" or a credit limit.
    private val currencyAmount = Regex("""(?<![a-z])(?:rs\.?|inr|₹)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", ignoreCase)
    private val balanceBefore = Regex("""(?:bal|balance|limit|lmt)[^0-9]{0,15}$""", ignoreCase)

    // SBI writes "debited by 45.0" with no currency at all.
    private val bareVerbAmount = Regex(
        """\b(?:debited|credited|sent|received|paid)\s+(?:by|for|with|of)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)\b""",
        ignoreCase,
    )

    private val balance = Regex(
        """(?:avl\.?|avbl\.?|avail(?:able)?\.?|clear|total|updated|new)\s*(?:bal(?:ance)?\.?|bal)\s*(?:is|:|-)?\s*(?:rs\.?|inr|₹)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
        ignoreCase,
    )

    // "A/c XX1234", "Acct XX315", "A/C *0270", "A/c No: XXXXXX2402", "A/C XXXXX268488".
    // Long numbers are cut to their last four digits.
    private val account = Regex(
        """\b(?:a/?c|acct|account|card|ac)\b\.?\s*(?:no\.?\s*[:.]?\s*)?(?:ending\s*(?:with\s*)?)?[x*.\s]*(\d{3,})""",
        ignoreCase,
    )

    // A handle with no dot after the @, so "me@gmail.com" isn't taken for one. Banks sometimes
    // mask the start ("*1265@upi"); the star is kept so it's clear the handle is partial.
    private val vpa = Regex("""(?<![a-z0-9._*-])(\*?[a-z0-9][a-z0-9._-]{1,255}@[a-z][a-z0-9]{1,63})\b(?!\.[a-z])""", ignoreCase)

    // Mandate ids look like handles ("9fdfa8e9…c224@ibl") but aren't anyone's VPA.
    private val mandateId = Regex("""^[0-9a-f]{32}@""", ignoreCase)

    private val labelledRef = listOf(
        Regex("""upi\s*(?:ref(?:erence)?|txn)\s*(?:id|no\.?|number)?\s*[:.#-]?\s*(\d{10,16})\b""", ignoreCase),
        Regex("""\b(?:ref(?:erence)?\s*(?:no\.?|number|id)?|refno|rrn|utr(?:\s*no\.?)?|txn\s*id|transaction\s*id)\s*[:.#-]?\s*(\d{10,16})\b""", ignoreCase),
        // "UPI/P2M/667414266978/…", "UPI/DRC/912025951785/…", "UPI:667566729530", HDFC's "(UPI 160541207196)"
        Regex("""\bupi\s*[:/ ]\s*(?:[a-z0-9]{2,6}/)?(\d{12})\b""", ignoreCase),
    )

    private val refundWords = Regex("""\b(?:refund(?:ed)?|reversal|reversed|revert(?:ed)?)\b""", ignoreCase)

    // SBI marks returned money with codes in the narration: DRC and ONRF are reversal/refund credits.
    private val refundCodes = Regex("""\bupi/(?:drc|onrf|rev|rvsl|rfnd)/""", ignoreCase)

    // Bare "credit"/"debit" also appear in "credit card", so only these phrases count.
    private val debitWords = Regex(
        """\b(?:debited|sent|paid|spent|withdrawn|transferred|deducted|charged|purchase|dr|trf to|debit alert|has (?:a )?debit)\b""",
        ignoreCase,
    )
    private val creditWords = Regex(
        """\b(?:credited|received|deposited|refunded|reversed|cr|added to|credit alert|has (?:a )?credit|trf from)\b""",
        ignoreCase,
    )

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

    fun accountLast4(body: String): String? = account.find(body)?.groupValues?.get(1)?.takeLast(4)

    fun vpa(body: String): String? =
        vpa.findAll(body).map { it.groupValues[1].lowercase() }.firstOrNull { !mandateId.containsMatchIn(it) }

    fun upiRef(body: String): String? =
        labelledRef.firstNotNullOfOrNull { it.find(body)?.groupValues?.get(1) }

    fun isRefund(body: String): Boolean = refundWords.containsMatchIn(body) || refundCodes.containsMatchIn(body)

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
