package com.mehul.inkless.parsing

/** UPI apps whose payment notifications we read, by Android package name. */
enum class UpiApp(val packageName: String, val label: String) {
    GPAY("com.google.android.apps.nbu.paisa.user", "Google Pay"),
    PHONEPE("com.phonepe.app", "PhonePe"),
    PAYTM("net.one97.paytm", "Paytm"),
    BHIM("in.org.npci.upiapp", "BHIM"),
    AMAZON("in.amazon.mShop.android.shopping", "Amazon Pay"),
    CRED("com.dreamplug.androidapp", "CRED"),
    SUPERMONEY("money.super.payments", "super.money"),
    NAVI("com.naviapp", "Navi"),
    ;

    companion object {
        fun of(packageName: String): UpiApp? = entries.firstOrNull { it.packageName == packageName }
    }
}

/**
 * Reads payment notifications from UPI apps. These carry the name the user actually recognises
 * ("Sharma Canteen" rather than "q829154@ybl") and cover UPI Lite payments, which send no bank SMS.
 * [text] is the notification's title and body joined on a new line.
 */
object NotificationReader {

    private val ignoreCase = setOf(RegexOption.IGNORE_CASE)
    private val whitespace = Regex("""\s+""")

    // Everything a UPI app notifies about that isn't money moving right now.
    private val notPayment = Regex(
        """\b(?:cashback|scratch ?card|reward|coupon|voucher|offer|win\b|won\b|earn|""" +
            """request(?:ed|ing|s)?\b|collect|reminder|due\b|due on|bill (?:is )?(?:generated|due)|pay now|expir|""" +
            """failed|declined|unsuccessful|pending|processing|could not|couldn't|reversal initiated|""" +
            """otp|verification code|refer|invite|kyc|recharge (?:now|expires|plan)|add money to)""",
        ignoreCase,
    )

    private val debitWords = Regex("""\b(?:paid|sent to|payment (?:of|to)|debited|you paid|transferred to)\b""", ignoreCase)
    private val creditWords = Regex("""\b(?:received|sent you|paid you|credited|added to your)\b""", ignoreCase)

    private val amount = Regex("""(?:₹|rs\.?|inr)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", ignoreCase)

    private val payeePatterns = listOf(
        // "₹45 paid to Sharma Canteen", "Sent ₹200 to Rohan", "Payment of ₹45 to Sharma Canteen successful"
        Regex("""\b(?:paid|sent|payment of [^ ]+|transferred)\s+(?:₹\s?[0-9.,]+\s+)?to\s+(.+?)(?:\s+(?:successful(?:ly)?|is successful|via|using|from|on)\b|[.!\n]|$)""", ignoreCase),
        // "Received ₹500 from Rohan Kumar", "₹500 received from Rohan"
        Regex("""\bfrom\s+(.+?)(?:\s+(?:on|via|in|to)\b|[.!\n]|$)""", ignoreCase),
        // "Rohan Kumar sent you ₹500", "Rohan paid you ₹500"
        Regex("""^(.+?)\s+(?:sent|paid) you\b""", ignoreCase),
    )

    fun read(packageName: String, text: String): SmsResult {
        val flat = text.replace(whitespace, " ").trim()
        if (notPayment.containsMatchIn(flat)) return SmsResult.Rejected(Rejection.PROMO)

        val paise = amount.find(flat)?.groupValues?.get(1)?.let(Amounts::toPaise)?.takeIf { it > 0 }
            ?: return SmsResult.Rejected(Rejection.NOT_A_TRANSACTION)
        val direction = direction(flat) ?: return SmsResult.Rejected(Rejection.NOT_A_TRANSACTION)

        // Patterns run on the original lines so a title line can't run into the body.
        val lines = text.lines().map { it.replace(whitespace, " ").trim() }.filter { it.isNotEmpty() }
        val payee = payeePatterns.firstNotNullOfOrNull { pattern ->
            lines.firstNotNullOfOrNull { line -> Fields.cleanName(pattern.find(line)?.groupValues?.get(1)) }
        }
        return SmsResult.Parsed(
            ParsedTxn(
                amountPaise = paise,
                direction = direction,
                vpa = Fields.vpa(flat),
                payee = payee,
                upiRef = Fields.upiRef(flat),
                isRefund = direction == TxnDirection.CREDIT && Fields.isRefund(flat),
                bankCode = UpiApp.of(packageName)?.name,
            ),
        )
    }

    private fun direction(text: String): TxnDirection? {
        val debit = debitWords.find(text)?.range?.first
        val credit = creditWords.find(text)?.range?.first
        return when {
            debit == null && credit == null -> null
            credit == null -> TxnDirection.DEBIT
            debit == null -> TxnDirection.CREDIT
            // "Rohan paid you" has both "paid" and "paid you"; the more specific credit phrase wins.
            credit <= debit -> TxnDirection.CREDIT
            else -> TxnDirection.DEBIT
        }
    }
}
