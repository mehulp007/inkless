package com.mehul.inkless.ask

import com.mehul.inkless.data.db.DatabaseProvider
import com.mehul.inkless.data.db.SpendPart
import com.mehul.inkless.data.db.dao.PaymentRow
import com.mehul.inkless.splits.FriendBalance
import com.mehul.inkless.splits.FriendsRepository
import com.mehul.inkless.ui.format.Money
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** One line under an answer: a payment, a category or a place, with its amount. */
data class AnswerLine(val title: String, val detail: String?, val amountPaise: Long, val transactionId: String? = null)

/**
 * [headlinePaise] is the number the answer is about, shown big; [understood] says how the
 * question was read ("Food & Canteen", "This month") so a misreading is easy to spot.
 */
data class Answer(
    val text: String,
    val lines: List<AnswerLine> = emptyList(),
    val headlinePaise: Long? = null,
    val understood: List<String> = emptyList(),
)

/** Answers a [QuerySpec] from the phone's own database. Nothing is sent anywhere. */
class AskEngine @Inject constructor(
    private val databases: DatabaseProvider,
    private val friends: FriendsRepository,
    private val clock: Clock,
) {
    suspend fun ask(question: String): Answer {
        val db = databases.current.value ?: return Answer("Log in or open the demo first.")
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(clock.withZone(zone))
        val categories = db.categoryDao().getAll().associate { it.id to it.name }
        val balances = friends.balances().first()
        val spec = AskParser.parse(question, today, categories, balances.map { it.friend.name })

        val start = spec.span.start.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = spec.span.end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val parts = db.spendDao().between(start, end)
        val payments = db.insightDao().paymentsSince(start).filter { it.timestamp < end }
        return answer(spec, parts, payments, categories, balances, zone)
    }

    companion object {
        private val DAY = DateTimeFormatter.ofPattern("d MMM")

        fun answer(
            spec: QuerySpec,
            parts: List<SpendPart>,
            payments: List<PaymentRow>,
            categoryNames: Map<String, String>,
            balances: List<FriendBalance>,
            zone: ZoneId,
        ): Answer {
            val plain = plainAnswer(spec, parts, payments, categoryNames, balances, zone)
            return plain.copy(understood = understood(spec, categoryNames))
        }

        /** How [spec] was read, as short labels: what's measured, any category, place or friend, and when. */
        fun understood(spec: QuerySpec, categoryNames: Map<String, String>): List<String> = listOfNotNull(
            when (spec.measure) {
                Measure.TOTAL -> "Total"
                Measure.COUNT -> "How many"
                Measure.BIGGEST -> "Biggest"
                Measure.DAILY_AVERAGE -> "Per day"
                Measure.TOP_CATEGORIES -> "Top categories"
                Measure.TOP_PLACES -> "Top places"
                Measure.FRIEND_BALANCE -> "What's owed"
            },
        ) + spec.categoryIds.mapNotNull { categoryNames[it] } +
            listOfNotNull(
                spec.place?.split(' ')?.joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } },
                spec.friend,
                spec.span.label.replaceFirstChar { it.uppercase() }.takeIf { spec.measure != Measure.FRIEND_BALANCE },
            )

        private fun plainAnswer(
            spec: QuerySpec,
            parts: List<SpendPart>,
            payments: List<PaymentRow>,
            categoryNames: Map<String, String>,
            balances: List<FriendBalance>,
            zone: ZoneId,
        ): Answer {
            if (spec.measure == Measure.FRIEND_BALANCE) return friendAnswer(spec.friend, balances)

            // Narrow down to the category or place asked about.
            val place = spec.place
            val matchingPayments = payments.filter { p ->
                (place == null || listOfNotNull(p.title, p.vpa).any { it.contains(place, ignoreCase = true) }) &&
                    (spec.categoryIds.isEmpty() || p.categoryId in spec.categoryIds || parts.any { it.transactionId == p.id && it.categoryId in spec.categoryIds })
            }
            val ids = matchingPayments.map { it.id }.toSet()
            val matchingParts = parts.filter { part ->
                (spec.categoryIds.isEmpty() || part.categoryId in spec.categoryIds) && (place == null || part.transactionId in ids)
            }
            val what = when {
                spec.categoryIds.isNotEmpty() -> spec.categoryIds.mapNotNull { categoryNames[it] }.joinToString(" and ")
                place != null -> place.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
                else -> null
            }
            val on = what?.let { " on $it" }.orEmpty()
            val period = spec.span.label
            val total = matchingParts.sumOf { it.amountPaise }

            fun line(p: PaymentRow) = AnswerLine(
                p.title ?: "Payment",
                Instant.ofEpochMilli(p.timestamp).atZone(zone).format(DAY),
                p.amountPaise,
                p.id,
            )
            val biggestFirst = matchingPayments.sortedByDescending { it.amountPaise }

            return when (spec.measure) {
                Measure.TOTAL -> if (matchingPayments.isEmpty() && total == 0L) {
                    Answer("Nothing spent$on $period.")
                } else {
                    val count = matchingPayments.size
                    Answer("${Money.format(total)}$on $period, over $count ${plural(count, "payment")}.", biggestFirst.take(5).map(::line), headlinePaise = total)
                }
                Measure.COUNT -> {
                    val count = matchingPayments.size
                    Answer(
                        if (count == 0) "No payments$on $period." else "$count ${plural(count, "payment")}$on $period, ${Money.format(total)} in all.",
                        matchingPayments.sortedByDescending { it.timestamp }.take(5).map(::line),
                    )
                }
                Measure.BIGGEST -> biggestFirst.firstOrNull()?.let { top ->
                    Answer("The biggest$on $period was ${Money.format(top.amountPaise)} to ${top.title ?: "someone"}.", biggestFirst.take(5).map(::line), headlinePaise = top.amountPaise)
                } ?: Answer("Nothing spent$on $period.")
                Measure.DAILY_AVERAGE -> {
                    val days = ChronoUnit.DAYS.between(spec.span.start, spec.span.end) + 1
                    Answer("About ${Money.format(total / days)} a day$on $period (${Money.format(total)} over $days ${plural(days.toInt(), "day")}).", headlinePaise = total / days)
                }
                Measure.TOP_CATEGORIES -> {
                    val top = parts.filter { it.categoryId != null }.groupBy { it.categoryId!! }
                        .mapValues { (_, list) -> list.sumOf { it.amountPaise } }
                        .filterValues { it > 0 }
                        .entries.sortedByDescending { it.value }
                    if (top.isEmpty()) {
                        Answer("Nothing spent $period.")
                    } else {
                        val first = top.first()
                        Answer(
                            "Most went on ${categoryNames[first.key] ?: "Other"} $period: ${Money.format(first.value)} of ${Money.format(parts.sumOf { it.amountPaise })}.",
                            top.take(5).map { AnswerLine(categoryNames[it.key] ?: "Other", null, it.value) },
                            headlinePaise = first.value,
                        )
                    }
                }
                Measure.TOP_PLACES -> {
                    val top = matchingPayments.filter { it.title != null }.groupBy { it.title!! }.entries
                        .sortedByDescending { e -> e.value.sumOf { it.amountPaise } }
                    if (top.isEmpty()) {
                        Answer("Nothing spent$on $period.")
                    } else {
                        Answer(
                            "Most went to ${top.first().key} $period.",
                            top.take(5).map { e -> AnswerLine(e.key, "${e.value.size} ${plural(e.value.size, "time")}", e.value.sumOf { it.amountPaise }) },
                        )
                    }
                }
                Measure.FRIEND_BALANCE -> friendAnswer(spec.friend, balances)
            }
        }

        private fun friendAnswer(name: String?, balances: List<FriendBalance>): Answer {
            val balance = balances.firstOrNull { it.friend.name == name } ?: return Answer("I don't know a friend called ${name ?: "that"}. Add them on the Friends screen.")
            val first = balance.friend.name.substringBefore(' ')
            val text = when {
                balance.netPaise > 0 -> "$first owes you ${Money.format(balance.netPaise)}."
                balance.netPaise < 0 -> "You owe $first ${Money.format(-balance.netPaise)}."
                else -> "You and $first are all square."
            }
            val lines = balance.openShares.map { AnswerLine(it.title ?: "Payment", "their part", it.sharePaise, it.transactionId) } +
                balance.openIous.map { AnswerLine(it.note ?: if (it.theyOwe) "You lent" else "You borrowed", if (it.theyOwe) "they owe you" else "you owe them", it.amountPaise) }
            return Answer(text, lines, headlinePaise = kotlin.math.abs(balance.netPaise).takeIf { it > 0 })
        }

        private fun plural(n: Int, word: String) = if (n == 1) word else word + "s"
    }
}
