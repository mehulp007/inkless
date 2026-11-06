package com.mehul.inkless.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.mehul.inkless.budget.BudgetTracker
import com.mehul.inkless.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Redraws the widget whenever what it shows changes: a new payment, an edit, a new budget, logging out. */
@OptIn(FlowPreview::class)
@Singleton
class WidgetRefresher @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val tracker: BudgetTracker,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch {
            tracker.snapshot()
                .map { it?.safeToSpendPaise }
                .distinctUntilChanged()
                .debounce(1_000)
                .collect { SafeToSpendWidget().updateAll(context) }
        }
    }
}
