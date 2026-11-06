package com.mehul.inkless.parsing

/**
 * Decides whether a message is worth parsing at all. Errs on the side of throwing things away:
 * a missed payment shows up in the "couldn't read" list or can be added by hand, but an OTP or
 * a personal message must never end up stored.
 */
object SmsFilter {

    private val ignoreCase = setOf(RegexOption.IGNORE_CASE)

    // "OTP is 123456", "123456 is your OTP", "verification code: 4821"
    private val otpWithCode = Regex(
        """(?:\botp\b|one[\s-]?time[\s-]?pass(?:word|code)?|verification code|security code|auth(?:entication)? code)""" +
            """[^0-9]{0,30}\b\d{4,8}\b|\b\d{4,8}\b[^0-9]{0,30}(?:\botp\b|one[\s-]?time[\s-]?pass)""",
        ignoreCase,
    )
    private val otpWord = Regex("""\botp\b|one[\s-]?time[\s-]?pass|verification code""", ignoreCase)

    // Money actually moved. "will be debited" is a warning, not a transaction.
    private val movedMoney = Regex(
        """(?<!will be )(?<!will get )\b(?:debited|credited|sent|received|paid|spent|withdrawn|deposited|transferred|deducted|refunded|reversed|trf to|trf from|has (?:a )?(?:credit|debit)|dr\. from|cr\. to)\b""",
        ignoreCase,
    )

    // Narrower than movedMoney for the OTP check: "the OTP sent to your phone" must still count as an OTP.
    private val accountMoved = Regex(
        """(?<!will be )\b(?:debited|credited|withdrawn|deposited|refunded|reversed)\b""",
        ignoreCase,
    )

    private val failed = Regex(
        """\b(?:failed|declined|unsuccessful|could not be (?:processed|completed)|not been processed|rejected)\b""",
        ignoreCase,
    )

    private val moneyCameBack = Regex("""(?<!will be )\b(?:reversed|refunded)\b""", ignoreCase)

    private val request = Regex("""\b(?:has requested|requested money|collect request|requesting rs|request of rs)""", ignoreCase)

    // Telecom recharges ("credited to your Airtel number") and reminders mention money without it
    // moving between bank accounts.
    private val notBankMoney = Regex(
        """\b(?:credited to your (?:airtel|jio|vi|vodafone|bsnl|mobile)|recharge (?:of|for|is successful)|successfully recharged|""" +
            """data pack|prepaid pack|talk ?time|validity extended|low balance|minimum balance|is due on|due date)""",
        ignoreCase,
    )

    private val promo = Regex(
        """\b(?:pre-?approved|personal loan|loan offer|apply now|eligible for|limit (?:increase|enhance)|upgrade your|""" +
            """exclusive offer|special offer|lowest (?:rate|emi)|instant loan|get up ?to|win\b|click here|download the app|""" +
            """complete your kyc|offer valid|t&c apply|insurance cover)""",
        ignoreCase,
    )

    // DLT headers end in -P for promotional traffic; banks never send transaction alerts that way.
    private val promoHeader = Regex("""-P$""", ignoreCase)

    private val accountish = Regex("""\b(?:a/?c|acct|account|card|upi|vpa)\b""", ignoreCase)

    fun check(sender: String, body: String): Rejection? {
        // OTPs first, whoever sent them. A real alert can still mention OTPs ("never share your OTP"),
        // so only a message where money visibly moved gets past a mention of one.
        if (otpWord.containsMatchIn(body) && !accountMoved.containsMatchIn(body)) return Rejection.OTP
        if (otpWithCode.containsMatchIn(body) && !accountMoved.containsMatchIn(body)) return Rejection.OTP

        val code = Senders.code(sender) ?: return Rejection.UNKNOWN_SENDER
        if (promoHeader.containsMatchIn(sender.trim())) return Rejection.PROMO
        val known = code in Senders.knownBanks

        if (notBankMoney.containsMatchIn(body)) return Rejection.NOT_A_TRANSACTION
        // "failed ... reversed to your a/c" is money coming back, which we do want.
        if (failed.containsMatchIn(body) && !moneyCameBack.containsMatchIn(body)) return Rejection.FAILED
        if (!movedMoney.containsMatchIn(body)) {
            return if (promo.containsMatchIn(body)) Rejection.PROMO else Rejection.NOT_A_TRANSACTION
        }
        if (request.containsMatchIn(body)) return Rejection.NOT_A_TRANSACTION
        if (promo.containsMatchIn(body) && !accountish.containsMatchIn(body)) return Rejection.PROMO

        // An unfamiliar header has to look very much like a bank alert to get through.
        if (!known && !accountish.containsMatchIn(body)) return Rejection.UNKNOWN_SENDER
        return null
    }
}
