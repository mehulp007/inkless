package com.mehul.inkless.ask

import com.mehul.inkless.data.seed.CategoryIds
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** What a question asks for. */
enum class Measure { TOTAL, COUNT, BIGGEST, DAILY_AVERAGE, TOP_CATEGORIES, TOP_PLACES, FRIEND_BALANCE }

/** A stretch of days, [start] to [end] inclusive, with how to say it ("this month", "in October"). */
data class DateSpan(val start: LocalDate, val end: LocalDate, val label: String)

/**
 * A question turned into a query: what to measure, over which days, and optionally only one
 * category, one place (matched against the payee) or one friend.
 */
data class QuerySpec(
    val measure: Measure,
    val span: DateSpan,
    val categoryIds: List<String> = emptyList(),
    val place: String? = null,
    val friend: String? = null,
)

/**
 * Understands the questions people actually type about their spending, in English or a bit of
 * Hinglish: "how much on food this month", "swiggy last week", "biggest payment in october",
 * "kitna kharcha kal", "how much does rohan owe me". No language model: keywords and a few
 * patterns, so it's instant, offline and predictable. Anything it doesn't recognise falls back to
 * "how much did I spend this month".
 */
object AskParser {

    fun parse(question: String, today: LocalDate, categories: Map<String, String>, friends: List<String>): QuerySpec {
        val q = " " + question.lowercase().replace(Regex("[^a-z0-9₹ ]"), " ").replace(Regex("\\s+"), " ").trim() + " "

        friendIn(q, friends)?.let { friend ->
            if (has(q, "owe", "owes", "udhaar", "baaki", "balance", "settle")) {
                return QuerySpec(Measure.FRIEND_BALANCE, allTime(today), friend = friend)
            }
        }

        val span = span(q, today)
        val categoryIds = categoriesIn(q, categories)
        val measure = when {
            has(q, "how many", "how often", "number of", "kitni baar", "kitne baar", "times") -> Measure.COUNT
            has(q, "biggest", "largest", "most expensive", "costliest", "highest", "sabse bada", "sabse mehenga") -> Measure.BIGGEST
            has(q, "average", "avg", "per day", "daily", "a day") -> Measure.DAILY_AVERAGE
            starts(q, "which categor", "what categor", "top categor", "categories", "breakdown", "where did my money", "where does my money") ->
                Measure.TOP_CATEGORIES
            has(q, "top places", "top shops", "top merchants", "which shop", "which place", "where did i spend most", "where do i spend most", "most at") ->
                Measure.TOP_PLACES
            else -> Measure.TOTAL
        }
        val place = if (categoryIds.isEmpty() && measure != Measure.TOP_PLACES && measure != Measure.TOP_CATEGORIES) placeIn(q) else null
        return QuerySpec(measure, span, categoryIds, place)
    }

    /** Whole words or phrases: "kal" doesn't match "kalyan". */
    private fun has(q: String, vararg phrases: String) = phrases.any { q.contains(" $it ") }

    /** Words starting with one of [prefixes]: "categor" for category and categories. */
    private fun starts(q: String, vararg prefixes: String) = prefixes.any { q.contains(" $it") }

    // Periods

    private val MONTHS = Month.entries.associateBy { it.name.lowercase().take(3) }

    private fun span(q: String, today: LocalDate): DateSpan {
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        Regex(" (?:last|past|pichle) (\\d{1,3}) days? ").find(q)?.let {
            val n = it.groupValues[1].toInt().coerceIn(1, 366)
            return DateSpan(today.minusDays(n - 1L), today, "in the last $n days")
        }
        return when {
            has(q, "today", "aaj") -> DateSpan(today, today, "today")
            has(q, "yesterday", "kal") -> today.minusDays(1).let { DateSpan(it, it, "yesterday") }
            has(q, "last week", "previous week", "pichle hafte", "pichhle hafte") ->
                DateSpan(monday.minusWeeks(1), monday.minusDays(1), "last week")
            has(q, "this week", "is hafte", "is week") -> DateSpan(monday, today, "this week")
            has(q, "last month", "previous month", "pichle mahine", "pichhle mahine") ->
                YearMonth.from(today).minusMonths(1).let { DateSpan(it.atDay(1), it.atEndOfMonth(), "last month") }
            has(q, "this year", "is saal") -> DateSpan(today.withDayOfYear(1), today, "this year")
            has(q, "ever", "all time", "so far", "total ever") -> allTime(today)
            else -> monthNamed(q, today) ?: YearMonth.from(today).let { DateSpan(it.atDay(1), today, "this month") }
        }
    }

    private fun monthNamed(q: String, today: LocalDate): DateSpan? {
        val match = Regex(" (jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*(?: (\\d{4}))? ").find(q) ?: return null
        val month = MONTHS.getValue(match.groupValues[1])
        val year = match.groupValues[2].toIntOrNull()
            // "October" means the last one that has started.
            ?: if (month.value > today.monthValue) today.year - 1 else today.year
        val ym = YearMonth.of(year, month)
        val end = if (ym == YearMonth.from(today)) today else ym.atEndOfMonth()
        val name = month.name.lowercase().replaceFirstChar { it.uppercase() }
        return DateSpan(ym.atDay(1), end, if (year == today.year) "in $name" else "in $name $year")
    }

    private fun allTime(today: LocalDate) = DateSpan(LocalDate.of(2000, 1, 1), today, "so far")

    // Categories

    /** Everyday words for each category, on top of the words in its name. */
    private val SYNONYMS = mapOf(
        CategoryIds.FOOD_CANTEEN to listOf("canteen", "chai", "tea", "coffee", "mess", "snacks", "tapri", "nashta"),
        CategoryIds.FOOD_DELIVERY to listOf("delivery", "ordering in", "ordered in", "takeaway"),
        CategoryIds.GROCERIES to listOf("grocery", "groceries", "essentials", "kirana"),
        CategoryIds.TRAVEL to listOf("travel", "auto", "autos", "cab", "cabs", "metro", "bus", "train", "rickshaw", "petrol", "commute"),
        CategoryIds.RECHARGE to listOf("recharge", "recharges", "subscription", "subscriptions", "data pack"),
        CategoryIds.COLLEGE to listOf("college", "stationery", "books", "xerox", "printouts", "prints", "fees"),
        CategoryIds.LAUNDRY to listOf("laundry", "hostel", "dhobi", "washing"),
        CategoryIds.FUN to listOf("fun", "outing", "outings", "movies", "movie", "party", "parties"),
        CategoryIds.SHOPPING to listOf("shopping", "clothes", "shoes"),
        CategoryIds.HEALTH to listOf("health", "medicine", "medicines", "doctor", "pharmacy", "medical"),
        CategoryIds.GIFTS to listOf("gift", "gifts", "birthday"),
        CategoryIds.LENT to listOf("lent", "lending"),
    )

    private fun categoriesIn(q: String, categories: Map<String, String>): List<String> {
        // "food" alone means both kinds.
        if (has(q, "food", "khana", "eating") && !has(q, "food delivery", "delivery", "canteen")) {
            return listOf(CategoryIds.FOOD_CANTEEN, CategoryIds.FOOD_DELIVERY).filter { it in categories }
        }
        val fromNames = categories.filter { (_, name) ->
            name.lowercase().split(Regex("[^a-z]+")).filter { it.length >= 4 && it != "friends" }.any { has(q, it) }
        }.keys
        val fromWords = SYNONYMS.filter { (id, words) -> id in categories && words.any { has(q, it) } }.keys
        return (fromNames + fromWords).distinct()
    }

    // Places and friends

    private val NOT_A_PLACE = setOf(
        "this", "last", "the", "my", "me", "food", "today", "yesterday", "week", "month", "year", "it", "them", "all",
        "average", "total", "past", "pichle", "is", "kal", "aaj", "a", "an", "spend", "spent", "payments", "upi",
        "on", "at", "to", "from", "in", "for", "with", "par", "pe", "and", "or",
    )

    /** "on swiggy", "at sharma canteen", "to ramu", "from zomato" → the words after it. */
    private fun placeIn(q: String): String? {
        val match = Regex(" (?:on|at|to|from|in|par|pe) ([a-z0-9]+(?: [a-z0-9]+)?) ").findAll(q)
            .map { it.groupValues[1] }
            .map { words -> words.split(' ').takeWhile { it !in NOT_A_PLACE && it !in PERIOD_WORDS }.joinToString(" ") }
            .firstOrNull { it.isNotBlank() && MONTHS.keys.none { m -> it.startsWith(m) } }
        return match
    }

    private val PERIOD_WORDS = setOf("days", "weeks", "hafte", "mahine", "saal", "so", "ever")

    private fun friendIn(q: String, friends: List<String>): String? = friends.firstOrNull { name ->
        val first = name.lowercase().substringBefore(' ')
        first.length >= 2 && (q.contains(" $first ") || q.contains(" ${name.lowercase()} "))
    }
}
