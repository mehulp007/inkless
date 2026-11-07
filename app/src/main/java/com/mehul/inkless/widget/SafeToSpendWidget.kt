package com.mehul.inkless.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider
import com.mehul.inkless.MainActivity
import com.mehul.inkless.R
import com.mehul.inkless.budget.BudgetSnapshot
import com.mehul.inkless.budget.BudgetText
import com.mehul.inkless.budget.BudgetTracker
import com.mehul.inkless.capture.CaptureRunner
import com.mehul.inkless.ui.theme.DarkInklessColors
import com.mehul.inkless.ui.theme.LightInklessColors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Home-screen widget: what's safe to spend today, and a Cash button that opens the quick-add sheet.
 * Glance draws it with its own Compose-like building blocks (Android widgets can't run Compose itself).
 * Text colours come from the app's palette in light and dark, and the card and button are drawable
 * shapes (the widget_ drawables and colours), so the launcher's light or dark setting picks the look
 * and the corners stay round on older Androids.
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

    // Two layouts: a short strip when the widget is one cell tall (2×1), the full card otherwise.
    override val sizeMode = SizeMode.Responsive(setOf(COMPACT, FULL))

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
            Content(context, live?.let(Shown::Budget) ?: shown)
        }
    }

    @Composable
    private fun Content(context: Context, shown: Shown) {
        val open = actionStartActivity(Intent(context, MainActivity::class.java))
        val cash = actionStartActivity(
            Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_QUICK_ADD_CASH)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
        val compact = LocalSize.current.height < FULL.height
        val card = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_background))
            .padding(horizontal = 16.dp, vertical = if (compact) 10.dp else 14.dp)
            .clickable(open)

        if (compact) {
            Row(card, verticalAlignment = Alignment.CenterVertically) {
                Column(GlanceModifier.defaultWeight()) { Figures(shown, compact = true) }
                if (shown != Shown.SignedOut) {
                    Spacer(GlanceModifier.width(8.dp))
                    CashPill(cash)
                }
            }
        } else {
            Column(card) {
                Column(GlanceModifier.defaultWeight()) { Figures(shown, compact = false) }
                if (shown != Shown.SignedOut) CashPill(cash)
            }
        }
    }

    @Composable
    private fun Figures(shown: Shown, compact: Boolean) {
        when (shown) {
            Shown.SignedOut -> {
                Label("Inkless")
                Text("Log in to see what's safe to spend", style = text(13.sp, Palette.MUTED), maxLines = 2)
            }
            Shown.NoBudget -> {
                Label("Safe today")
                Text("Set your allowance to see it here", style = text(13.sp, Palette.MUTED), maxLines = 2)
            }
            is Shown.Budget -> {
                val snapshot = shown.snapshot
                val over = snapshot.safeToSpendPaise < 0
                val amount = BudgetText.safeToSpendAmount(snapshot)
                Label(if (over) "Over today" else "Safe today")
                Text(
                    amount,
                    modifier = GlanceModifier.semantics {
                        contentDescription = if (over) "$amount over today's share" else "$amount safe to spend today"
                    },
                    style = text(if (compact) 22.sp else 30.sp, if (over) Palette.DANGER else Palette.INK, FontWeight.Medium),
                    maxLines = 1,
                )
                if (!compact) {
                    Spacer(GlanceModifier.height(2.dp))
                    Text(BudgetText.widgetLine(snapshot), style = text(12.sp, Palette.MUTED), maxLines = 1)
                }
            }
        }
    }

    @Composable
    private fun Label(text: String) {
        Text(text, style = text(13.sp, Palette.PRIMARY, FontWeight.Medium), maxLines = 1)
    }

    @Composable
    private fun CashPill(onClick: Action) {
        Box(
            GlanceModifier
                .background(ImageProvider(R.drawable.widget_pill))
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clickable(onClick)
                .semantics { contentDescription = "Add cash" },
            contentAlignment = Alignment.Center,
        ) {
            Text("+ Cash", style = text(14.sp, Palette.ON_PRIMARY, FontWeight.Bold))
        }
    }

    private fun text(size: TextUnit, color: ColorProvider, weight: FontWeight = FontWeight.Normal) =
        TextStyle(fontSize = size, color = color, fontWeight = weight)

    /** The app's light and dark colours; the launcher's own light or dark setting picks one. */
    private object Palette {
        val INK = ColorProvider(day = LightInklessColors.ink, night = DarkInklessColors.ink)
        val MUTED = ColorProvider(day = LightInklessColors.muted, night = DarkInklessColors.muted)
        val PRIMARY = ColorProvider(day = LightInklessColors.primary, night = DarkInklessColors.primary)
        val ON_PRIMARY = ColorProvider(day = LightInklessColors.onPrimary, night = DarkInklessColors.onPrimary)
        val DANGER = ColorProvider(day = LightInklessColors.danger, night = DarkInklessColors.danger)
    }

    private companion object {
        val COMPACT = DpSize(140.dp, 48.dp)
        val FULL = DpSize(140.dp, 110.dp)
    }
}

class SafeToSpendWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = SafeToSpendWidget()
}
