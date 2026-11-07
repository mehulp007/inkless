package com.mehul.inkless.ui.insights

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mehul.inkless.R
import com.mehul.inkless.data.db.entity.RecurringEntity
import com.mehul.inkless.insights.AccuracyReport
import com.mehul.inkless.insights.AlertText
import com.mehul.inkless.ml.WeekAccuracy
import com.mehul.inkless.ui.components.CategoryTile
import com.mehul.inkless.ui.components.InklessCard
import com.mehul.inkless.ui.components.InklessTopBar
import com.mehul.inkless.ui.components.LedgerRow
import com.mehul.inkless.ui.components.PageHeadline
import com.mehul.inkless.ui.components.SectionHeader
import com.mehul.inkless.ui.components.WarmCard
import com.mehul.inkless.ui.format.Money
import com.mehul.inkless.ui.theme.Inkless
import com.mehul.inkless.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Alerts, payments that come round again, and how well the guessing is going. */
@Composable
fun InsightsScreen(
    onBack: () -> Unit,
    onOpenCategory: (String) -> Unit,
    viewModel: InsightsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    InsightsContent(state, onBack, onOpenCategory, now = System.currentTimeMillis())
}

@Composable
fun InsightsContent(state: InsightsState, onBack: () -> Unit, onOpenCategory: (String) -> Unit, now: Long) {
    val colors = Inkless.colors
    Surface(Modifier.fillMaxSize(), color = colors.background) {
        Column(Modifier.fillMaxSize()) {
            InklessTopBar("Insights", onBack = onBack)
            if (state.loading) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = colors.primary) }
                return@Column
            }
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = Spacing.gutter, vertical = Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.xl),
            ) {
                PageHeadline("What your money's been up to.", lead = "Worked out on this phone from your own payments.")

                Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    SectionHeader("Spending alerts")
                    if (state.alerts.isEmpty()) {
                        Muted("Nothing unusual lately. When a category runs well above its usual week or day, it shows up here.")
                    }
                    state.alerts.forEach { alert ->
                        WarmCard(onClick = { onOpenCategory(alert.categoryId) }) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                                Icon(painterResource(R.drawable.icon_trending_up), contentDescription = null, tint = colors.onWarm, modifier = Modifier.size(20.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        AlertText.title(alert, state.categories[alert.categoryId]?.name ?: "Spending"),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = colors.ink,
                                    )
                                    Text(AlertText.body(alert), style = MaterialTheme.typography.bodySmall, color = colors.onWarm)
                                }
                                Icon(painterResource(R.drawable.icon_chevron_right), contentDescription = null, tint = colors.onWarm, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    SectionHeader("Coming up")
                    if (state.recurring.isEmpty()) {
                        Muted("Payments that come round every week, month or year show up here once Inkless has seen a few.")
                    } else {
                        InklessCard(padding = PaddingValues(horizontal = Spacing.l, vertical = Spacing.xs)) {
                            state.recurring.forEachIndexed { i, item ->
                                if (i > 0) HorizontalDivider(color = colors.hairline)
                                LedgerRow(
                                    title = item.name,
                                    amount = "~${Money.format(item.typicalAmountPaise)}",
                                    meta = recurringMeta(item, now),
                                    leading = { CategoryTile(item.categoryId, size = 36.dp) },
                                )
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    SectionHeader("How well Inkless guesses")
                    state.accuracy?.let { Accuracy(it) }
                }
            }
        }
    }
}

@Composable
private fun Accuracy(report: AccuracyReport) {
    val colors = Inkless.colors
    if (report.guesses == 0) {
        Muted(
            "Every payment that arrives gets a guess from what you've sorted before. Sort a few and this fills in. " +
                "It has learned from ${report.learnedFrom} ${if (report.learnedFrom == 1) "payment" else "payments"} so far.",
        )
        return
    }
    InklessCard {
        report.recentRate?.let {
            Text("${(it * 100).roundToInt()}% right lately", style = Inkless.type.moneyMedium, color = colors.ink)
            Text(
                "${report.recentRight} of the last ${report.recentGuesses} guesses · learned from ${report.learnedFrom} payments",
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted,
            )
        }
        AccuracyChart(report.weeks, Modifier.fillMaxWidth().height(110.dp).padding(top = Spacing.l))
        Row(Modifier.fillMaxWidth().padding(top = Spacing.s)) {
            Text(report.weeks.first().weekStart.format(DAY), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = colors.muted)
            Text("this week", style = MaterialTheme.typography.labelSmall, color = colors.muted)
        }
        report.overrideRate?.let {
            Text(
                "You changed ${report.overridden} of the ${report.filed} payments Inkless filed by itself (${(it * 100).roundToInt()}%).",
                Modifier.padding(top = Spacing.m),
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted,
            )
        }
    }
}

/** Each week's share of right guesses; weeks without any are a faint stub. */
@Composable
private fun AccuracyChart(weeks: List<WeekAccuracy>, modifier: Modifier) {
    val color = Inkless.colors.primary
    val empty = Inkless.colors.tintStrong
    val guide = Inkless.colors.hairline
    val summary = accuracySummary(weeks)
    Canvas(modifier.semantics { contentDescription = summary }) {
        val slot = size.width / weeks.size
        val barWidth = slot * 0.6f
        weeks.forEachIndexed { index, week ->
            val rate = week.rate
            val height = if (rate == null) 2.dp.toPx() else (size.height * rate).coerceAtLeast(2.dp.toPx())
            drawRoundRect(
                color = if (rate == null) empty else color,
                topLeft = Offset(slot * index + (slot - barWidth) / 2, size.height - height),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(3.dp.toPx()),
            )
        }
        // 50% and 100% guides.
        listOf(0.5f, 1f).forEach { level ->
            val y = size.height * (1 - level)
            drawLine(guide, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
        }
    }
}

/** "Right guesses each week, oldest first: none, 70%, 90%." */
internal fun accuracySummary(weeks: List<WeekAccuracy>): String =
    "Right guesses each week, oldest first: " + weeks.joinToString { w -> w.rate?.let { "${(it * 100).roundToInt()}%" } ?: "none" } + "."

/** "every month · next 14 Nov", or "· due now" once the date has passed. */
internal fun recurringMeta(item: RecurringEntity, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val every = when (item.intervalDays) {
        7 -> "every week"
        365 -> "every year"
        else -> "every month"
    }
    val due = if (item.nextExpectedAt < now) "due now" else "next ${Instant.ofEpochMilli(item.nextExpectedAt).atZone(zone).format(DAY)}"
    return "$every · $due"
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = Inkless.colors.muted)
}

private val DAY = DateTimeFormatter.ofPattern("d MMM")
