package com.mehul.inkless.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.mehul.inkless.MainActivity
import com.mehul.inkless.budget.BudgetSnapshot
import com.mehul.inkless.budget.BudgetText
import com.mehul.inkless.budget.BudgetTracker
import com.mehul.inkless.capture.CaptureRunner
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Home-screen widget: what's safe to spend today, and a Cash button that opens the quick-add sheet.
 * Glance draws it with its own Compose-like building blocks (Android widgets can't run Compose itself).
 */
class SafeToSpendWidget : GlanceAppWidget() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun runner(): CaptureRunner
        fun tracker(): BudgetTracker
    }

    private sealed interface Shown {
        data object SignedOut : Shown
        data object NoBudget : Shown
        data class Budget(val snapshot: BudgetSnapshot) : Shown
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val deps = EntryPointAccessors.fromApplication(context, Deps::class.java)
        val shown = when (val ledger = deps.runner().currentLedger()) {
            null -> Shown.SignedOut
            else -> deps.tracker().current(ledger)?.let(Shown::Budget) ?: Shown.NoBudget
        }
        provideContent {
            // Glance keeps a widget's session alive for a while and recomposes it on updates, so
            // follow the budget live rather than only what was read when the session began.
            val live by remember { deps.tracker().snapshot() }.collectAsState(initial = (shown as? Shown.Budget)?.snapshot)
            GlanceTheme { Content(context, live?.let(Shown::Budget) ?: shown) }
        }
    }

    @Composable
    private fun Content(context: Context, shown: Shown) {
        val open = actionStartActivity(Intent(context, MainActivity::class.java))
        Column(
            modifier = GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(16.dp).padding(12.dp).clickable(open),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.Start,
        ) {
            when (shown) {
                Shown.SignedOut -> Text("Log in to Inkless to see what you can spend", style = small())
                Shown.NoBudget -> Text("Set your allowance in Inkless to see what you can spend today", style = small())
                is Shown.Budget -> {
                    val snapshot = shown.snapshot
                    Text(
                        BudgetText.safeToSpendAmount(snapshot),
                        style = TextStyle(
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (snapshot.safeToSpendPaise < 0) GlanceTheme.colors.error else GlanceTheme.colors.onSurface,
                        ),
                    )
                    Text(BudgetText.safeToSpendLabel(snapshot), style = small())
                }
            }
            Spacer(GlanceModifier.height(8.dp))
            Button(
                text = "+ Cash",
                onClick = actionStartActivity(
                    Intent(context, MainActivity::class.java)
                        .setAction(MainActivity.ACTION_QUICK_ADD_CASH)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                ),
            )
        }
    }

    @Composable
    private fun small() = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurfaceVariant)
}

class SafeToSpendWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = SafeToSpendWidget()
}
