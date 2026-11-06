package com.mehul.inkless.parsing

/** The one entry point the app uses: filter first, then the sender's bank parser, then the generic one. */
class SmsReader(bankParsers: List<BankParser> = emptyList()) {

    private val byCode: Map<String, BankParser> = bankParsers.flatMap { p -> p.codes.map { it to p } }.toMap()

    fun read(sender: String, body: String): SmsResult {
        SmsFilter.check(sender, body)?.let { return SmsResult.Rejected(it) }

        val code = Senders.code(sender)
        val txn = code?.let { byCode[it] }?.parse(body)?.copy(bankCode = code)
            ?: GenericUpiParser.parse(body, code)
        return if (txn != null) SmsResult.Parsed(txn) else SmsResult.Unparsed
    }
}
