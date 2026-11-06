package com.mehul.inkless.parsing

/** The one entry point the app uses: filter first, then the sender's bank parser, then the generic one. */
class SmsReader(bankParsers: List<BankParser> = emptyList()) {

    private val byCode: Map<String, BankParser> = bankParsers.flatMap { p -> p.codes.map { it to p } }.toMap()
    private val whitespace = Regex("""\s+""")

    fun read(sender: String, body: String): SmsResult {
        // Banks split the same alert over lines differently (and phones re-wrap them), so the
        // patterns only ever see single spaces.
        val text = body.replace(whitespace, " ").trim()

        SmsFilter.check(sender, text)?.let { return SmsResult.Rejected(it) }

        val code = Senders.code(sender)
        val txn = code?.let { byCode[it] }?.parse(text)?.copy(bankCode = code)
            ?: GenericUpiParser.parse(text, code)
        return if (txn != null) SmsResult.Parsed(txn) else SmsResult.Unparsed
    }
}
