package com.mehul.inkless.categorize

/** How payments are grouped under one merchant for merchant memory. */
object MerchantKeys {

    private val spaces = Regex("""\s+""")

    /**
     * The UPI ID when the SMS has one; otherwise the payee's name, so "SHARMA CANTEEN" in an SBI
     * alert is still remembered. Null when there's nothing to go on.
     */
    fun of(vpa: String?, payee: String?): String? =
        vpa?.lowercase()
            ?: payee?.trim()?.lowercase()?.replace(spaces, " ")?.takeIf { it.isNotEmpty() }?.let { "name:$it" }
}
