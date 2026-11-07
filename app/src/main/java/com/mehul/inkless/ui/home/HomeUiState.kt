package com.mehul.inkless.ui.home

import com.mehul.inkless.budget.BudgetSnapshot
import com.mehul.inkless.data.db.dao.CategoryTotal
import com.mehul.inkless.data.db.dao.TransactionListItem
import com.mehul.inkless.data.db.entity.CategoryEntity
import com.mehul.inkless.data.db.entity.Direction
import com.mehul.inkless.data.db.entity.TransactionItemEntity
import com.mehul.inkless.data.db.entity.TransactionStatus
import com.mehul.inkless.data.db.entity.TransactionType
import com.mehul.inkless.ui.format.Titles
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Account bits the home screen shows around the ledger. */
data class HomeAccount(
    val isDemo: Boolean = false,
    /** Set while the signed-in user hasn't clicked the verification link yet. */
    val unverifiedEmail: String? = null,
    val verificationSent: Boolean = false,
    /** Signed in, SMS capture not set up, and the user hasn't said "not now" yet. */
    val offerSmsSetup: Boolean = false,
    /** First login on this phone: the backup is still coming down. */
    val restoring: Boolean = false,
    /** A new account on this phone: open the welcome checklist once. */
    val showOnboarding: Boolean = false,
)

sealed interface HomeBudget {
    /** Signed out, or still loading. */
    data object Hidden : HomeBudget

    /** Signed in, no allowance set yet: Home offers to set one. */
    data object NotSet : HomeBudget

    data class Shown(val snapshot: BudgetSnapshot) : HomeBudget
}

sealed interface HomeUiState {
    data object Loading : HomeUiState

    data class Ready(
        val month: YearMonth,
        val today: LocalDate,
        val canGoForward: Boolean,
        val spentPaise: Long,
        val tabs: List<CategoryTab>,
    ) : HomeUiState
}

data class CategoryTab(
    val key: String,
    val title: String,
    val emoji: String,
    val colorArgb: Long,
    val totalPaise: Long,
    val count: Int,
    /** Every day with payments, newest first. */
    val days: List<DayGroup>,
    /** In the current month, today and yesterday; in a past month, every day. */
    val recent: List<DayGroup> = days,
    /** In the current month, everything before yesterday, folded into one line. */
    val earlier: EarlierGroup? = null,
)

data class DayGroup(
    val date: LocalDate,
    /** Spending that day, less refunds. */
    val spentPaise: Long,
    val rows: List<TransactionRow>,
    /** A refund came in that day, so [spentPaise] is net of it. */
    val net: Boolean = false,
)

/**
 * "2–4 Nov · 8 payments", folded: the older days of the current month. Money that came in (the
 * allowance) is listed on its own, since it isn't spending.
 */
data class EarlierGroup(
    val from: LocalDate,
    val to: LocalDate,
    val paymentCount: Int,
    /** The biggest categories, most spent first, at most three. */
    val topCategories: List<String>,
    val moreCategories: Boolean,
    val spentPaise: Long,
    val days: List<DayGroup>,
    val income: List<TransactionRow>,
)

data class TransactionRow(
    val id: String,
    val title: String,
    val vpa: String?,
    val categoryId: String?,
    val categoryName: String? = null,
    val amountPaise: Long,
    val isCredit: Boolean,
    /** Money back from a shop: it counts against its category's spending. */
    val isRefund: Boolean = false,
    /** Still waiting for the user to say what it was. */
    val toSort: Boolean = false,
    /** Between the user's own accounts: shown, but never counted as spending. */
    val isTransfer: Boolean = false,
    val emoji: String,
    val colorArgb: Long,
    val timestamp: Long,
    /** A broken-down payment shows under its biggest item, with a "+N more" badge for the rest. */
    val moreItems: Int = 0,
    /** Friends' parts of a split payment; only the rest is the user's spending. */
    val friendsSharePaise: Long = 0,
) {
    val myPaise: Long get() = amountPaise - friendsSharePaise

    /** Money in that isn't a refund, like the allowance: listed, never counted. */
    val isIncome: Boolean get() = isCredit && !isRefund && !isTransfer

    /** What this row adds to a day's spending: its part if it's spending, minus a refund. */
    val spendPaise: Long get() = when {
        isTransfer || isIncome -> 0
        isRefund -> -amountPaise
        else -> myPaise
    }
}

internal object HomeStateBuilder {

    const val ALL_TAB_KEY = "all"
    private const val NEUTRAL_COLOR = 0xFF78716C

    fun build(
        month: YearMonth,
        today: LocalDate,
        zone: ZoneId,
        categories: List<CategoryEntity>,
        transactions: List<TransactionListItem>,
        totals: List<CategoryTotal>,
        items: List<TransactionItemEntity> = emptyList(),
    ): HomeUiState.Ready {
        val categoriesById = categories.associateBy { it.id }
        val totalsById = totals.associateBy { it.categoryId }
        val itemsByPayment = items.groupBy { it.transactionId }

        val rows = transactions.map { txn ->
            val category = txn.categoryId?.let(categoriesById::get)
            val isCredit = txn.direction == Direction.CREDIT
            val title = txn.merchantName ?: txn.payeeRaw ?: txn.vpa ?: Titles.fallback(txn.type)
            TransactionRow(
                id = txn.id,
                title = title,
                vpa = txn.vpa?.takeIf { it != title },
                categoryId = txn.categoryId,
                categoryName = category?.name,
                amountPaise = txn.amountPaise,
                isCredit = isCredit,
                isRefund = isCredit && txn.type != TransactionType.SELF_TRANSFER &&
                    (txn.type == TransactionType.REFUND || txn.categoryId != null),
                toSort = txn.status == TransactionStatus.NEEDS_REVIEW,
                isTransfer = txn.type == TransactionType.SELF_TRANSFER,
                emoji = category?.emoji ?: when {
                    txn.type == TransactionType.SELF_TRANSFER -> "🔁"
                    isCredit -> "💰"
                    else -> "❓"
                },
                colorArgb = category?.colorArgb ?: NEUTRAL_COLOR,
                timestamp = txn.timestamp,
                moreItems = (txn.itemCount - 1).coerceAtLeast(0),
                friendsSharePaise = txn.friendsSharePaise,
            )
        }
        val rowsByDay = rows.groupBy { dayOf(it, zone) }

        // What a payment spent in one category: its item(s) there if it's broken down, else all or
        // nothing. A refund takes away from the category it came back to.
        fun spentIn(row: TransactionRow, categoryId: String): Long {
            if (row.isRefund) return if (row.categoryId == categoryId) -row.amountPaise else 0
            if (row.isCredit || row.isTransfer) return 0
            val parts = itemsByPayment[row.id] ?: return if (row.categoryId == categoryId) row.myPaise else 0
            return parts.filter { it.categoryId == categoryId }.sumOf { it.amountPaise }
        }

        val spent = totals.sumOf { it.totalPaise }
        val allTab = CategoryTab(
            key = ALL_TAB_KEY,
            title = "All",
            emoji = "🧾",
            colorArgb = NEUTRAL_COLOR,
            totalPaise = spent,
            count = rows.size,
            days = groupByDay(rows, zone) { _, dayRows -> dayRows.sumOf { it.spendPaise } },
        )
        val categoryTabs = categories.map { category ->
            val categoryRows = rows.filter { it.categoryId == category.id }
            CategoryTab(
                key = category.id,
                title = category.name,
                emoji = category.emoji,
                colorArgb = category.colorArgb,
                totalPaise = totalsById[category.id]?.totalPaise ?: 0L,
                count = categoryRows.size,
                // Counts this category's part of every payment that day, listed here or not, like the tab total.
                days = groupByDay(categoryRows, zone) { date, _ ->
                    rowsByDay[date].orEmpty().sumOf { spentIn(it, category.id) }
                },
            )
        }

        val isCurrentMonth = month == YearMonth.from(today)
        return HomeUiState.Ready(
            month = month,
            today = today,
            canGoForward = month < YearMonth.from(today),
            spentPaise = spent,
            tabs = (listOf(allTab) + categoryTabs).map { if (isCurrentMonth) fold(it, today) else it },
        )
    }

    /** Today and yesterday stay as they are; the days before them fold into "Earlier this month". */
    private fun fold(tab: CategoryTab, today: LocalDate): CategoryTab {
        val yesterday = today.minusDays(1)
        val (recent, older) = tab.days.partition { it.date >= yesterday }
        if (older.isEmpty()) return tab
        val income = older.flatMap { it.rows }.filter { it.isIncome }
        val spendingDays = older.mapNotNull { day ->
            day.rows.filterNot { it.isIncome }.takeIf { it.isNotEmpty() }?.let { day.copy(rows = it) }
        }
        val spendingRows = spendingDays.flatMap { it.rows }
        val byCategory = spendingRows.filter { it.spendPaise > 0 }
            .groupBy { it.categoryName ?: "Other" }
            .mapValues { (_, r) -> r.sumOf { it.spendPaise } }
            .entries.sortedByDescending { it.value }
            .map { it.key }
        val earlier = EarlierGroup(
            // The range covers the spending folded away; money in is listed with its own date.
            from = (spendingDays.ifEmpty { older }).minOf { it.date },
            to = (spendingDays.ifEmpty { older }).maxOf { it.date },
            paymentCount = spendingRows.count { !it.isCredit && !it.isTransfer },
            topCategories = byCategory.take(3),
            moreCategories = byCategory.size > 3,
            spentPaise = spendingDays.sumOf { it.spentPaise },
            days = spendingDays,
            income = income,
        )
        return tab.copy(recent = recent, earlier = earlier)
    }

    // Rows arrive newest first, and groupBy keeps that order for both days and rows.
    private fun groupByDay(
        rows: List<TransactionRow>,
        zone: ZoneId,
        spent: (LocalDate, List<TransactionRow>) -> Long,
    ): List<DayGroup> =
        rows.groupBy { dayOf(it, zone) }
            .map { (date, dayRows) ->
                DayGroup(date = date, spentPaise = spent(date, dayRows), rows = dayRows, net = dayRows.any { it.isRefund })
            }

    private fun dayOf(row: TransactionRow, zone: ZoneId): LocalDate = Instant.ofEpochMilli(row.timestamp).atZone(zone).toLocalDate()
}
