package com.mehul.inkless.ui.wrapped

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.mehul.inkless.data.db.DatabaseProvider
import com.mehul.inkless.data.db.entity.CategoryEntity
import com.mehul.inkless.insights.Wrapped
import com.mehul.inkless.insights.WrappedStats
import com.mehul.inkless.ui.navigation.WrappedRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

data class WrappedState(
    val month: YearMonth,
    val stats: WrappedStats? = null,
    val categories: Map<String, CategoryEntity> = emptyMap(),
    val canGoForward: Boolean = false,
    /** Nobody signed in and no demo open: there's nothing to look back on. */
    val unavailable: Boolean = false,
)

@HiltViewModel
class WrappedViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val databases: DatabaseProvider,
    private val clock: Clock,
) : ViewModel() {

    private val zone = ZoneId.systemDefault()
    private val today get() = LocalDate.now(clock.withZone(zone))

    private val _state = MutableStateFlow(WrappedState(YearMonth.parse(savedState.toRoute<WrappedRoute>().month)))
    val state: StateFlow<WrappedState> = _state.asStateFlow()

    init {
        load(_state.value.month)
    }

    fun previous() = load(_state.value.month.minusMonths(1))

    fun next() {
        if (_state.value.canGoForward) load(_state.value.month.plusMonths(1))
    }

    private fun load(month: YearMonth) {
        _state.value = WrappedState(month, canGoForward = month < YearMonth.from(today))
        val db = databases.current.value
        if (db == null) {
            _state.value = _state.value.copy(unavailable = true)
            return
        }
        viewModelScope.launch {
            fun millis(day: LocalDate) = day.atStartOfDay(zone).toInstant().toEpochMilli()
            val start = millis(month.atDay(1))
            val end = millis(month.plusMonths(1).atDay(1))
            val previousStart = millis(month.minusMonths(1).atDay(1))
            val stats = Wrapped.of(
                month = month,
                parts = db.spendDao().between(start, end),
                previousParts = db.spendDao().between(previousStart, start),
                payments = db.insightDao().paymentsSince(start).filter { it.timestamp < end },
                today = today,
                zone = zone,
            )
            _state.value = _state.value.copy(stats = stats, categories = db.categoryDao().getAll().associateBy { it.id })
        }
    }
}
