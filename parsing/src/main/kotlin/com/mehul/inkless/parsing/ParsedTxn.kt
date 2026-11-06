package com.mehul.inkless.parsing

enum class TxnDirection { DEBIT, CREDIT }

/** What a bank SMS says happened. Only parsed fields leave this module, never the message text. */
data class ParsedTxn(
    val amountPaise: Long,
    val direction: TxnDirection,
    val vpa: String? = null,
    val payee: String? = null,
    val accountLast4: String? = null,
    val upiRef: String? = null,
    val balancePaise: Long? = null,
    /** Credit that says it's a refund or reversal of an earlier payment. */
    val isRefund: Boolean = false,
    /** Short sender code like "HDFCBK", when the sender looked like a bank. */
    val bankCode: String? = null,
)

/** Why a message was turned away before parsing. */
enum class Rejection {
    /** One-time passwords are dropped outright and never stored. */
    OTP,
    PROMO,
    FAILED,
    /** Collect requests, balance alerts, reminders: money didn't actually move. */
    NOT_A_TRANSACTION,
    /** A phone number or a sender that isn't a bank. */
    UNKNOWN_SENDER,
}

sealed interface SmsResult {
    data class Parsed(val txn: ParsedTxn) : SmsResult

    /** Looks like a bank transaction, but none of the parsers could read it. */
    data object Unparsed : SmsResult

    data class Rejected(val reason: Rejection) : SmsResult
}
