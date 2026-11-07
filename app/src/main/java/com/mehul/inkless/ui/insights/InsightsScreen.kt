package com.mehul.inkless.ui.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mehul.inkless.R
import com.mehul.inkless.data.db.entity.CategoryEntity
import com.mehul.inkless.data.db.entity.RecurringEntity
import com.mehul.inkless.insights.AccuracyReport
import com.mehul.inkless.insights.AlertText
import com.mehul.inkless.ml.WeekAccuracy
import com.mehul.inkless.ui.components.CategoryTile
import com.mehul.inkless.ui.theme.Inkless
import com.mehul.inkless.ui.format.Money
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import kotlin.math.roundToInt

/** Alerts, recurring payments, the month wrapped up, Ask Inkless, and how well the guessing is going. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
    onBack: () -> Unit,
    onAsk: () -> Unit,
    onWrapped: (YearMonth) -> Unit,
    viewModel: InsightsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Insights") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ClickCard(onClick = onAsk) {
                Text("Ask Inkless", style = MaterialTheme.typography.titleMedium)
                Text(
                    "\"How much on food this month?\" · \"Biggest payment in October\" · \"How much does Rohan owe me?\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            state.alerts.forEach { alert ->
                val category = state.categories[alert.categoryId]
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            AlertText.title(alert, category?.name ?: "Spending"),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Text(AlertText.body(alert), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }

            ClickCard(onClick = { onWrapped(state.wrappedMonth) }) {
                Text(
                    "Your ${state.wrappedMonth.month.getDisplayName(TextStyle.FULL, LocalConfiguration.current.locales[0])}, wrapped",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "The month in a few numbers, with a picture to share.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Section("Coming up") {
                if (state.recurring.isEmpty()) {
                    Muted("Payments that come round every week, month or year show up here once Inkless has seen a few.")
                }
                state.recurring.forEach { RecurringRow(it, state.categories[it.categoryId]) }
            }

            Section("How well Inkless guesses") {
                state.accuracy?.let { AccuracySection(it) }
            }
        }
    }
}

@Composable
private fun AccuracySection(report: AccuracyReport) {
    if (report.guesses == 0) {
        Muted(
            "Every payment that arrives gets a guess from what you've sorted before. " +
                "Sort a few and this fills in. It has learned from ${report.learnedFrom} ${if (report.learnedFrom == 1) "payment" else "payments"} so far.",
        )
        return
    }
    AccuracyChart(report.weeks, Modifier.fillMaxWidth().height(120.dp))
    val first = report.weeks.first().weekStart.format(DateTimeFormatter.ofPattern("d MMM"))
    Row(Modifier.fillMaxWidth()) {
        Text(first, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("this week", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    report.recentRate?.let {
        Text(
            "Right ${report.recentRight} of the last ${report.recentGuesses} guesses (${(it * 100).roundToInt()}%).",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
    }
    val overrides = report.overrideRate?.let {
        " You changed ${report.overridden} of the ${report.filed} payments Inkless filed by itself (${(it * 100).roundToInt()}%)."
    }.orEmpty()
    Muted("Learned from ${report.learnedFrom} payments you've sorted.$overrides")
}

/** Each week's share of right guesses; weeks without any are a faint stub. */
@Composable
private fun AccuracyChart(weeks: List<WeekAccuracy>, modifier: Modifier) {
    val color = Inkless.colors.primary
    val empty = Inkless.colors.tintStrong
    val guide = Inkless.colors.hairline
    Canvas(modifier) {
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

@Composable
private fun RecurringRow(item: RecurringEntity, category: CategoryEntity?) {
    val every = when (item.intervalDays) {
        7 -> "every week"
        365 -> "every year"
        else -> "every month"
    }
    val next = Instant.ofEpochMilli(item.nextExpectedAt).atZone(ZoneId.systemDefault()).toLocalDate()
    val due = if (item.nextExpectedAt < System.currentTimeMillis()) "due now" else "next around ${next.format(DateTimeFormatter.ofPattern("d MMM"))}"
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CategoryTile(item.categoryId, Modifier.padding(end = 12.dp), size = 36.dp)
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge)
            Text("$every · $due", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("~${Money.format(item.typicalAmountPaise)}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ClickCard(onClick: () -> Unit, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
