package com.mehul.inkless.categorize

import com.mehul.inkless.data.seed.CategoryIds

/**
 * Built-in guesses from merchant names and UPI IDs. Matching works on words: short keywords must
 * be a whole word ("ola" matches "ola cabs" but not "kolapur"), longer ones are distinctive enough
 * to match inside a word ("instamart" in "swiggyinstamart"). More specific keywords are listed
 * first, so Instamart becomes groceries before Swiggy can make it food delivery.
 */
object Keywords {

    const val CONFIDENCE = 0.85f

    private const val INSIDE_WORD_MIN_LENGTH = 6

    private val table: List<Pair<String, String>> = buildList {
        fun add(category: String, vararg words: String) = words.forEach { add(it to category) }

        add(CategoryIds.GROCERIES, "instamart", "blinkit", "zepto", "bigbasket", "jiomart", "dmart", "grofers", "reliancefresh", "kirana", "supermarket", "provision")
        add(CategoryIds.FOOD_DELIVERY, "swiggy", "zomato", "eatsure", "eatclub", "magicpin")
        add(CategoryIds.TRAVEL, "uber", "olacabs", "ola", "rapido", "irctc", "redbus", "makemytrip", "goibibo", "ixigo", "abhibus", "metro", "dmrc", "fastag", "indianoil", "hpcl", "bpcl", "petrol", "auto", "railway", "indigo")
        add(CategoryIds.RECHARGE, "jio", "airtel", "vodafone", "bsnl", "netflix", "spotify", "hotstar", "jiocinema", "primevideo", "youtube", "googleplay", "recharge")
        add(CategoryIds.HEALTH, "pharmeasy", "1mg", "netmeds", "apollo", "medplus", "pharmacy", "chemist", "medical", "medicos", "hospital", "clinic", "diagnostic")
        add(CategoryIds.FUN, "bookmyshow", "district", "pvr", "inox", "cinepolis", "steam", "playstation", "gaming")
        add(CategoryIds.SHOPPING, "amazon", "flipkart", "myntra", "ajio", "meesho", "nykaa", "decathlon", "croma", "shoppers")
        add(CategoryIds.COLLEGE, "xerox", "stationery", "stationers", "printout", "photocopy", "university", "college", "books", "exam")
        add(CategoryIds.LAUNDRY, "laundry", "dhobi", "laundromat", "drycleaner", "hostel")
        add(CategoryIds.FOOD_CANTEEN, "canteen", "cafe", "chai", "tea", "mess", "bakery", "bakers", "restaurant", "dhaba", "juice", "snacks", "sweets", "bhojnalaya", "kitchen", "foods", "tapri", "starbucks", "mcdonalds", "dominos", "pizza", "burger", "biryani", "kfc")
    }

    private val nonWord = Regex("[^a-z0-9]+")

    /** The category for the first keyword that matches, checking the UPI ID's name part and the payee. */
    fun categoryFor(vpa: String?, payee: String?): String? {
        val words = listOfNotNull(vpa?.substringBefore('@'), payee)
            .flatMap { it.lowercase().split(nonWord) }
            .filter { it.isNotEmpty() }
        if (words.isEmpty()) return null
        return table.firstOrNull { (keyword, _) -> words.any { word -> matches(word, keyword) } }?.second
    }

    private fun matches(word: String, keyword: String): Boolean =
        if (keyword.length >= INSIDE_WORD_MIN_LENGTH) keyword in word else word == keyword
}
