package com.mehul.inkless.parsing

/**
 * Indian commercial SMS come from DLT headers like "VM-HDFCBK", "AD-SBIUPI-S" or "JX-ICICIT-T":
 * a two-letter operator/circle prefix, a six-character brand code, and sometimes a suffix for
 * the message category. Personal messages come from phone numbers instead.
 */
object Senders {

    private val dltHeader = Regex("^[A-Z]{2}-([A-Z0-9]{6})(?:-[A-Z])?$")
    private val bareCode = Regex("^[A-Z0-9]{6}$")

    /** Brand codes of banks and payments banks we know send transaction alerts. */
    val knownBanks: Set<String> = setOf(
        "HDFCBK", "HDFCBN",
        "SBIUPI", "SBIINB", "SBIPSG", "CBSSBI", "ATMSBI", "SBMSMS",
        "ICICIB", "ICICIT", "ICICIO",
        "AXISBK", "AXISMR",
        "KOTAKB", "KOTAKM",
        "PNBSMS", "PUNBNK",
        "BOBTXN", "BOBSMS", "BARODA",
        "CANBNK", "CANARA",
        "UNIONB", "UBINBK",
        "IDFCFB", "INDUSB", "YESBNK", "FEDBNK", "AUBANK", "RBLBNK", "DBSBNK", "SCBANK",
        "CITIBK", "HSBCIN", "IOBCHN", "IOBBNK", "CENTBK", "IDBIBK", "BOIIND", "UCOBNK",
        "KVBANK", "SIBSMS", "EQUTAS", "UJJIVN", "INDBNK",
        "PAYTMB", "AIRBNK", "JIOPBS", "FINOBK",
    )

    /** "VM-HDFCBK-S" -> "HDFCBK"; null for phone numbers and anything not shaped like a header. */
    fun code(sender: String): String? {
        val s = sender.trim().uppercase()
        dltHeader.matchEntire(s)?.let { return it.groupValues[1] }
        return s.takeIf { bareCode.matches(it) && it.any(Char::isLetter) }
    }

    fun isKnownBank(sender: String): Boolean = code(sender) in knownBanks
}
