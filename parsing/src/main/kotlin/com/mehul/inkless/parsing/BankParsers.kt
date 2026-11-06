package com.mehul.inkless.parsing

/** One exact message shape for a bank, with named groups for the fields it carries. */
class Template(pattern: String, val direction: TxnDirection) {
    val regex = Regex(pattern, RegexOption.IGNORE_CASE)
}

/**
 * Tries a bank's known message shapes in order. Groups a template doesn't have (say, no VPA in
 * an SBI alert) are filled in by the shared [Fields] extractors.
 */
open class TemplateBankParser(
    override val codes: Set<String>,
    private val templates: List<Template>,
) : BankParser {

    override fun parse(body: String): ParsedTxn? {
        for (template in templates) {
            val match = template.regex.find(body) ?: continue
            fun group(name: String): String? = runCatching { match.groups[name]?.value }.getOrNull()

            val amount = group("amount")?.let(Amounts::toPaise)?.takeIf { it > 0 } ?: continue
            return ParsedTxn(
                amountPaise = amount,
                direction = template.direction,
                vpa = group("vpa")?.lowercase() ?: Fields.vpa(body),
                payee = Fields.cleanName(group("payee")),
                accountLast4 = group("acct")?.takeLast(4) ?: Fields.accountLast4(body),
                upiRef = group("ref") ?: Fields.upiRef(body),
                balancePaise = group("bal")?.let(Amounts::toPaise) ?: Fields.balancePaise(body),
                isRefund = template.direction == TxnDirection.CREDIT && Fields.isRefund(body),
            )
        }
        return null
    }
}

private const val AMT = """[0-9][0-9,]*(?:\.[0-9]{1,2})?"""

/** State Bank of India. "-SBI" at the end, amounts often without "Rs". */
object SbiParser : TemplateBankParser(
    codes = setOf("SBIUPI", "SBIINB", "CBSSBI", "ATMSBI", "SBIPSG"),
    templates = listOf(
        // Dear UPI user A/C X2402 debited by 40.00 on date 02Sep26 trf to TEMPTATION JAIPU Refno 497519427964 …
        Template("""A/C X*(?<acct>\d+) debited by (?<amount>$AMT) on date \S+ trf to (?<payee>.+?) Refno (?<ref>\d+)""", TxnDirection.DEBIT),
        // Your A/C XXXXX268488 has credit for UPI/DRC/912025951785/07102026/ of Rs 280.00 on 08/10/26. Avl Bal Rs 8,086.21.-SBI
        Template("""A/C X*(?<acct>\d+) has (?:a )?credit for \S+ of Rs\.? ?(?<amount>$AMT) on \S+?\.? Avl Bal Rs\.? ?(?<bal>$AMT)""", TxnDirection.CREDIT),
        Template("""A/C X*(?<acct>\d+) has (?:a )?debit for \S+ of Rs\.? ?(?<amount>$AMT) on \S+?\.? Avl Bal Rs\.? ?(?<bal>$AMT)""", TxnDirection.DEBIT),
        // Dear SBI User, your A/c X1234-credited by Rs.500 on 05Nov26 transfer from ROHAN K Ref No 512345678907 -SBI
        Template("""A/c X*(?<acct>\d+)-? ?credited by Rs\.? ?(?<amount>$AMT) on \S+ transfer from (?<payee>.+?) Ref No (?<ref>\d+)""", TxnDirection.CREDIT),
    ),
)

/** HDFC Bank: line-per-field alerts ("Sent Rs.… / From HDFC Bank A/C … / To … / On … / Ref …"). */
object HdfcParser : TemplateBankParser(
    codes = setOf("HDFCBK", "HDFCBN"),
    templates = listOf(
        Template("""Sent Rs\.? ?(?<amount>$AMT) From HDFC Bank A/C \*?X*(?<acct>\d+) To (?<payee>.+?) On \S+ Ref (?<ref>\d+)""", TxnDirection.DEBIT),
        // UPI Mandate: Sent Rs.1.00 from HDFC Bank A/c 0270 To Moj 23/10/26 Ref 185767689388
        Template("""UPI Mandate: Sent Rs\.? ?(?<amount>$AMT) from HDFC Bank A/c \*?X*(?<acct>\d+) To (?<payee>.+?) \d{1,2}/\d{1,2}/\d{2,4} Ref (?<ref>\d+)""", TxnDirection.DEBIT),
        // Credit Alert! Rs.20.00 credited to HDFC Bank A/c XX0270 on 31-10-26 from VPA *1265@upi (UPI 160541207196)
        Template("""Rs\.? ?(?<amount>$AMT) credited to HDFC Bank A/c \*?X*(?<acct>\d+) on \S+ from VPA (?<vpa>\S+?) \(UPI (?<ref>\d+)\)""", TxnDirection.CREDIT),
        // Rs.1499.00 debited from a/c **1234 on 05-11-26 to VPA amazonpay@apl (UPI Ref No 512345678906)
        Template("""Rs\.? ?(?<amount>$AMT) debited from a/c \**X*(?<acct>\d+) on \S+ to VPA (?<vpa>\S+?) \(UPI Ref No\.? ?(?<ref>\d+)\)""", TxnDirection.DEBIT),
    ),
)

/** ICICI Bank: one sentence each way, payee named next to the opposite verb. */
object IciciParser : TemplateBankParser(
    codes = setOf("ICICIB", "ICICIT", "ICICIO"),
    templates = listOf(
        // ICICI Bank Acct XX315 debited for Rs 40.00 on 05-Nov-26; BFF 2024 14 credited. UPI:667566729530. …
        Template("""Acct X*(?<acct>\d+) debited for Rs\.? ?(?<amount>$AMT) on \S+; (?<payee>.+?) credited\. UPI:(?<ref>\d+)""", TxnDirection.DEBIT),
        // Dear Customer, Acct XX315 is credited with Rs 160.00 on 03-Nov-26 from SOMEONE. UPI:667300758675-ICICI Bank.
        Template("""Acct X*(?<acct>\d+) is credited with Rs\.? ?(?<amount>$AMT) on \S+ from (?<payee>.+?)\. UPI:(?<ref>\d+)""", TxnDirection.CREDIT),
    ),
)

/** Axis Bank: "INR … debited / A/c no. … / date, time / UPI/P2M/<ref>/<payee>". */
object AxisParser : TemplateBankParser(
    codes = setOf("AXISBK", "AXISMR"),
    templates = listOf(
        Template("""INR (?<amount>$AMT) debited A/c no\. X*(?<acct>\d+) \S+?,? \S+ UPI/P2[AM]/(?<ref>\d+)/(?<payee>.+?)(?: Not you| Axis Bank|$)""", TxnDirection.DEBIT),
        Template("""INR (?<amount>$AMT) credited A/c no\. X*(?<acct>\d+) \S+?,? \S+ UPI/P2[AM]/(?<ref>\d+)/(?<payee>.+?)(?: Not you| Axis Bank|$)""", TxnDirection.CREDIT),
        // INR 2000.00 credited to Axis Bank A/c XX3654 on 22-08-26 at 18:05:57 IST. Ref: NEFT/SBIN1…/SOMEONE.
        Template("""INR (?<amount>$AMT) credited to Axis Bank A/c X*(?<acct>\d+) on \S+ at \S+ IST\. Ref: (?:NEFT|IMPS|RTGS)/\w+/(?<payee>[^./]+)""", TxnDirection.CREDIT),
    ),
)

/** Every bank-specific parser. Banks not listed here go through [GenericUpiParser]. */
object BankParsers {
    val all: List<BankParser> = listOf(SbiParser, HdfcParser, IciciParser, AxisParser)
}
