package com.mehul.inkless.ui.category

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.mehul.inkless.data.db.dao.MerchantSpend
import com.mehul.inkless.data.db.entity.CategoryEntity
import com.mehul.inkless.data.repo.LedgerRepository
import com.mehul.inkless.ui.navigation.CategoryRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import javax.inject.Inject

data class CategoryDetail(
    val loading: Boolean = true,
    val category: CategoryEntity? = null,
    /** Oldest first, this month last. */
    val months: List<Pair<YearMonth, Long>> = emptyList(),
    val topMerchants: List<MerchantSpend> = emptyList(),
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

    val state: StateFlow<CategoryDetail> = combine(
        repository.category(id),
        repository.spendBetween(millis(firstMonth), millis(thisMonth.plusMonths(1))),
        repository.topMerchants(id, millis(thisMonth), millis(thisMonth.plusMonths(1)), TOP_MERCHANTS),
    ) { category, parts, merchants ->
        val byMonth = parts.filter { it.categoryId == id }
            .groupBy { YearMonth.from(Instant.ofEpochMilli(it.timestamp).atZone(clock.zone)) }
            .mapValues { (_, monthParts) -> monthParts.sumOf { it.amountPaise } }
        CategoryDetail(
            loading = false,
            category = category,
            months = (0 until TREND_MONTHS).map { firstMonth.plusMonths(it.toLong()) }.map { it to (byMonth[it] ?: 0L) },
            topMerchants = merchants,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryDetail())

    /** Null clears it. */
    fun setBudget(paise: Long?) {
        viewModelScope.launch { repository.setCategoryBudget(id, paise) }
    }

    private fun millis(month: YearMonth) = month.atDay(1).atStartOfDay(clock.zone).toInstant().toEpochMilli()

    private companion object {
        const val TREND_MONTHS = 6
        const val TOP_MERCHANTS = 5
    }
}
