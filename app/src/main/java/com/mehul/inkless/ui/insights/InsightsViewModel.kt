package com.mehul.inkless.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mehul.inkless.data.db.DatabaseProvider
import com.mehul.inkless.data.db.entity.CategoryEntity
import com.mehul.inkless.data.db.entity.RecurringEntity
import com.mehul.inkless.insights.AccuracyReport
import com.mehul.inkless.insights.Recurring
import com.mehul.inkless.insights.SpendAlert
import com.mehul.inkless.insights.SpendAlerts
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

data class InsightsState(
    val loading: Boolean = true,
    val accuracy: AccuracyReport? = null,
    val alerts: List<SpendAlert> = emptyList(),
    val recurring: List<RecurringEntity> = emptyList(),
    val categories: Map<String, CategoryEntity> = emptyMap(),
    /** The month the Wrapped card opens: last month, the last complete one. */
    val wrappedMonth: YearMonth = YearMonth.now(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InsightsViewModel @Inject constructor(
    databases: DatabaseProvider,
    alerts: SpendAlerts,
    private val clock: Clock,
) : ViewModel() {

    private val zone = ZoneId.systemDefault()

    val state: StateFlow<InsightsState> = databases.current.filterNotNull().flatMapLatest { db ->
        val dao = db.insightDao()
        // Recurring payments and alerts are worked out fresh each time the screen opens.
        val fresh = flow {
            Recurring.refresh(db, clock.millis(), zone)
            emit(alerts.current(db))
        }.onStart { emit(emptyList()) }
        combine(
            dao.observeOutcomes(),
            dao.observeTrainingRows().map { it.size },
            dao.observeRecurring(),
            db.categoryDao().observeAll(),
            fresh,
        ) { outcomes, learned, recurring, categories, current ->
            val today = LocalDate.now(clock.withZone(zone))
            InsightsState(
                loading = false,
                accuracy = AccuracyReport.of(outcomes, learned, today, zone),
                alerts = current,
                recurring = recurring,
                categories = categories.associateBy { it.id },
                wrappedMonth = YearMonth.from(today).minusMonths(1),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsState())
}
