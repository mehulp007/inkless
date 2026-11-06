package com.mehul.inkless.ui.home

import com.mehul.inkless.data.db.dao.CategoryTotal
import com.mehul.inkless.data.db.dao.TransactionListItem
import com.mehul.inkless.data.db.entity.CategoryEntity
import com.mehul.inkless.data.db.entity.Direction
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

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
    val days: List<DayGroup>,
)

data class DayGroup(
    val date: LocalDate,
    val spentPaise: Long,
    val rows: List<TransactionRow>,
)

data class TransactionRow(
    val id: String,
    val title: String,
    val vpa: String?,
    val categoryId: String?,
    val amountPaise: Long,
    val isCredit: Boolean,
    val emoji: String,
    val colorArgb: Long,
    val timestamp: Long,
)

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
    ): HomeUiState.Ready {
        val categoriesById = categories.associateBy { it.id }
        val totalsById = totals.associateBy { it.categoryId }

        val rows = transactions.map { txn ->
            val category = txn.categoryId?.let(categoriesById::get)
            val isCredit = txn.direction == Direction.CREDIT
            val title = txn.merchantName ?: txn.payeeRaw ?: txn.vpa ?: "Unknown"
            TransactionRow(
                id = txn.id,
                title = title,
                vpa = txn.vpa?.takeIf { it != title },
                categoryId = txn.categoryId,
                amountPaise = txn.amountPaise,
                isCredit = isCredit,
                emoji = category?.emoji ?: if (isCredit) "💰" else "❓",
                colorArgb = category?.colorArgb ?: NEUTRAL_COLOR,
                timestamp = txn.timestamp,
            )
        }

        val spent = totals.sumOf { it.totalPaise }
        val allTab = CategoryTab(
            key = ALL_TAB_KEY,
            title = "All",
            emoji = "🧾",
            colorArgb = NEUTRAL_COLOR,
            totalPaise = spent,
            count = rows.size,
            days = groupByDay(rows, zone),
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
                days = groupByDay(categoryRows, zone),
            )
        }

        return HomeUiState.Ready(
            month = month,
            today = today,
            canGoForward = month < YearMonth.from(today),
            spentPaise = spent,
            tabs = listOf(allTab) + categoryTabs,
        )
    }

    // Rows arrive newest first, and groupBy keeps that order for both days and rows.
    private fun groupByDay(rows: List<TransactionRow>, zone: ZoneId): List<DayGroup> =
        rows.groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
            .map { (date, dayRows) ->
                DayGroup(
                    date = date,
                    spentPaise = dayRows.filterNot { it.isCredit }.sumOf { it.amountPaise },
                    rows = dayRows,
                )
            }
}
