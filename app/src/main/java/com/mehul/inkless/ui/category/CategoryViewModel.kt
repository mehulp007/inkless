package com.mehul.inkless.ui.category

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.mehul.inkless.data.db.dao.MerchantSpend
import com.mehul.inkless.data.db.entity.CaptureSource
import com.mehul.inkless.data.db.entity.Direction
import com.mehul.inkless.data.db.entity.CategoryEntity
import com.mehul.inkless.data.repo.LedgerRepository
import com.mehul.inkless.ui.format.Titles
import com.mehul.inkless.ui.navigation.CategoryRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/** One of today's payments on the category page: "Sharma Canteen · 13:10 · Bank SMS + notification". */
data class CategoryStop(
    val id: String,
    val title: String,
    val timestamp: Long,
    val amountPaise: Long,
    val isRefund: Boolean,
    val sources: Set<CaptureSource>,
)

data class CategoryDetail(
    val loading: Boolean = true,
    val category: CategoryEntity? = null,
    /** Oldest first, this month last. */
    val months: List<Pair<YearMonth, Long>> = emptyList(),
    val topMerchants: List<MerchantSpend> = emptyList(),
    /** Today's payments in this category, newest first. */
    val today: List<CategoryStop> = emptyList(),
) {
    val thisMonthPaise: Long get() = months.lastOrNull()?.second ?: 0
}

@HiltViewModel
class CategoryViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: LedgerRepository,
    private val clock: Clock,
) : ViewModel() {

    private val id = savedState.toRoute<CategoryRoute>().categoryId
    private val thisMonth = YearMonth.now(clock)
    private val firstMonth = thisMonth.minusMonths(TREND_MONTHS - 1L)

    val month: YearMonth = thisMonth

    private val today = LocalDate.now(clock)

    val state: StateFlow<CategoryDetail> = combine(
        repository.category(id),
        repository.spendBetween(millis(firstMonth), millis(thisMonth.plusMonths(1))),
        repository.topMerchants(id, millis(thisMonth), millis(thisMonth.plusMonths(1)), TOP_MERCHANTS),
        repository.transactionsBetween(dayMillis(today), dayMillis(today.plusDays(1))),
    ) { category, parts, merchants, todays ->
        val byMonth = parts.filter { it.categoryId == id }
            .groupBy { YearMonth.from(Instant.ofEpochMilli(it.timestamp).atZone(clock.zone)) }
            .mapValues { (_, monthParts) -> monthParts.sumOf { it.amountPaise } }
        CategoryDetail(
            loading = false,
            category = category,
            months = (0 until TREND_MONTHS).map { firstMonth.plusMonths(it.toLong()) }.map { it to (byMonth[it] ?: 0L) },
            topMerchants = merchants,
            today = todays.filter { it.categoryId == id }.map { txn ->
                CategoryStop(
                    id = txn.id,
                    title = txn.merchantName ?: txn.payeeRaw ?: txn.vpa ?: Titles.fallback(txn.type),
                    timestamp = txn.timestamp,
                    amountPaise = txn.amountPaise,
                    isRefund = txn.direction == Direction.CREDIT,
                    sources = txn.sources,
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryDetail())

    /** Null clears it. */
    fun setBudget(paise: Long?) {
        viewModelScope.launch { repository.setCategoryBudget(id, paise) }
    }

    private fun millis(month: YearMonth) = dayMillis(month.atDay(1))

    private fun dayMillis(day: LocalDate) = day.atStartOfDay(clock.zone).toInstant().toEpochMilli()

    private companion object {
        const val TREND_MONTHS = 6
        const val TOP_MERCHANTS = 5
    }
}
