package com.mehul.inkless.ui.money

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mehul.inkless.budget.BudgetMath
import com.mehul.inkless.budget.BudgetSnapshot
import com.mehul.inkless.budget.BudgetTracker
import com.mehul.inkless.budget.DigestScheduler
import com.mehul.inkless.data.db.entity.CategoryEntity
import com.mehul.inkless.data.repo.LedgerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** A category with a monthly budget, and what it has taken this calendar month. */
data class CategoryBudget(val category: CategoryEntity, val spentPaise: Long) {
    val budgetPaise: Long get() = category.monthlyBudgetPaise ?: 0
}

data class MoneyState(
    val loading: Boolean = true,
    /** Null while no allowance is set. */
    val snapshot: BudgetSnapshot? = null,
    val month: YearMonth = YearMonth.now(),
    val budgets: List<CategoryBudget> = emptyList(),
    val semesterName: String = "",
    val semesterPaise: Long = 0,
    /** The Wrapped card opens last month, the last complete one. */
    val wrappedMonth: YearMonth = YearMonth.now().minusMonths(1),
)

@HiltViewModel
class MoneyViewModel @Inject constructor(
    repository: LedgerRepository,
    budgetTracker: BudgetTracker,
    private val digest: DigestScheduler,
    private val clock: Clock,
) : ViewModel() {

    private val today = LocalDate.now(clock)
    private val month = YearMonth.from(today)

    private fun millis(day: LocalDate) = day.atStartOfDay(clock.zone).toInstant().toEpochMilli()

    private val budgets = combine(
        repository.categories(),
        repository.categoryTotalsBetween(millis(month.atDay(1)), millis(month.plusMonths(1).atDay(1))),
    ) { categories, totals ->
        val spent = totals.associate { it.categoryId to it.totalPaise }
        categories.filter { (it.monthlyBudgetPaise ?: 0) > 0 }.map { CategoryBudget(it, spent[it.id] ?: 0) }
    }

    private val semester = BudgetMath.semester(today).let { (start, end) ->
        repository.spendBetween(millis(start), millis(end)).map { parts -> parts.sumOf { it.amountPaise } }
    }

    val state: StateFlow<MoneyState> = combine(budgetTracker.snapshot(), budgets, semester) { snapshot, list, semesterPaise ->
        MoneyState(
            loading = false,
            snapshot = snapshot,
            month = month,
            budgets = list,
            semesterName = BudgetMath.semesterName(today),
            semesterPaise = semesterPaise.coerceAtLeast(0),
            wrappedMonth = month.minusMonths(1),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MoneyState())

    private val _weeklySummary = MutableStateFlow(digest.enabled)
    val weeklySummary: StateFlow<Boolean> = _weeklySummary.asStateFlow()

    fun setWeeklySummary(on: Boolean) {
        digest.enabled = on
        _weeklySummary.value = on
    }
}
