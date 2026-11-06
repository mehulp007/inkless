package com.mehul.inkless.parsing

/** Reads one bank's alerts. Returns null when the message isn't in a shape it knows. */
interface BankParser {
    /** Sender brand codes this parser handles, e.g. "HDFCBK". */
    val codes: Set<String>

    fun parse(body: String): ParsedTxn?
}

/**
 * Fallback for any bank: an amount, a debit/credit verb, and whatever of VPA, account, reference
 * and payee the shared patterns can find. Bank parsers exist for formats this gets wrong.
 */
object GenericUpiParser {

    private val ignoreCase = setOf(RegexOption.IGNORE_CASE)

    private val payeePatterns = listOf(
        // SBI: "trf to SHARMA CANTEEN Refno 5123..."
        Regex("""\btrf to (.+?)\s+(?:ref\s*no|refno|ref\b)""", ignoreCase),
        // ICICI: "debited for Rs 45.00 on 05-Nov-26; SHARMA CANTEEN credited."
        Regex(""";\s*([^;]+?)\s+credited\b""", ignoreCase),
        // Axis and others: "UPI/P2M/512345678901/SHARMA CANTEEN"
        Regex("""\bupi/p2[am]/\d+/([^/\n]+?)(?:/|\s+not you|\s+-|\.\s|$)""", ignoreCase),
        // NEFT/IMPS credits: "Ref: NEFT/SBIN1262951…/JAIN."
        Regex("""\b(?:neft|imps|rtgs)/[a-z0-9]+/([^/.]+)""", ignoreCase),
        // "paid to X via UPI", "sent to X from …"
        Regex("""\bpaid\b.*?\bto\s+(.+?)\s+via\b""", ignoreCase),
        Regex("""\bsent to\s+(.+?)(?:\s+from\b|\s+upi\b|\.\s)""", ignoreCase),
        // "to SHARMA CANTEEN on 05-11-26", "from ROHAN K on ...", "To Moj 23/10/26"
        Regex(
            """\b(?:to|from)\s+(?!a/?c\b|your\b|vpa\b|ac\b|account\b|bank\b)([a-z][a-z0-9 .&'-]{1,40}?)\s+(?:on|ref|upi|via|dated|\d{1,2}[/-]\d{1,2}[/-]\d{2,4})\b""",
            ignoreCase,
        ),
    )

    fun parse(body: String, bankCode: String? = null): ParsedTxn? {
        val amount = Fields.amountPaise(body) ?: return null
        val direction = Fields.direction(body) ?: return null
        val vpa = Fields.vpa(body)
        val payee = payeePatterns.firstNotNullOfOrNull { Fields.cleanName(it.find(body)?.groupValues?.get(1)) }
        return ParsedTxn(
            amountPaise = amount,
            direction = direction,
            vpa = vpa,
            payee = payee,
            accountLast4 = Fields.accountLast4(body),
            upiRef = Fields.upiRef(body),
            balancePaise = Fields.balancePaise(body),
            isRefund = direction == TxnDirection.CREDIT && Fields.isRefund(body),
            bankCode = bankCode,
        )
    }
}
