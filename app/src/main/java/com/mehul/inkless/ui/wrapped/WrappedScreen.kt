package com.mehul.inkless.ui.wrapped

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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mehul.inkless.R
import com.mehul.inkless.data.db.entity.CategoryEntity
import com.mehul.inkless.insights.WrappedStats
import com.mehul.inkless.ui.format.Money
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.absoluteValue

/** A month in a few numbers, and a picture of them to share. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WrappedScreen(onBack: () -> Unit, viewModel: WrappedViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(onBack, state.month, state.canGoForward, viewModel::previous, viewModel::next) {
        val stats = state.stats
        if (stats == null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        if (stats.payments == 0) {
            Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                Text("Nothing spent in ${monthName(stats.month)}.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        val lines = WrappedText.lines(stats, state.categories)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text(WrappedText.title(stats), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(
                        Money.format(stats.totalPaise),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(WrappedText.sub(stats), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            lines.forEach { (label, value) ->
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(value, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            Button(
                onClick = {
                    WrappedImage.share(
                        context, stats, lines.take(WrappedText.IMAGE_LINES), WrappedText.title(stats), Money.format(stats.totalPaise), WrappedText.sub(stats),
                    )
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text("Share as a picture") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Scaffold(
    onBack: () -> Unit,
    month: YearMonth,
    canGoForward: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    content: @Composable () -> Unit,
) {
    androidx.compose.material3.Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wrapped") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = onPrevious) { Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = "Previous month") }
                    Text(monthName(month), style = MaterialTheme.typography.titleSmall)
                    IconButton(onClick = onNext, enabled = canGoForward) {
                        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = "Next month")
                    }
                },
            )
        },
    ) { padding -> Box(Modifier.padding(padding)) { content() } }
}

private fun monthName(month: YearMonth) = month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())

/** The words on the Wrapped card, shared by the screen and the picture. */
object WrappedText {

    /** How many lines fit in the picture. */
    const val IMAGE_LINES = 5

    fun title(stats: WrappedStats) = "My ${monthName(stats.month)}"

    fun sub(stats: WrappedStats): String {
        val change = stats.change ?: return "spent over ${stats.payments} payments"
        val previous = monthName(stats.month.minusMonths(1))
        return when {
            change > 0 -> "spent · $change% more than $previous"
            change < 0 -> "spent · ${change.absoluteValue}% less than $previous"
            else -> "spent · same as $previous"
        }
    }

    fun lines(stats: WrappedStats, categories: Map<String, CategoryEntity>): List<Pair<String, String>> = listOfNotNull(
        stats.topCategoryId?.let { id ->
            val c = categories[id]
            "Most went on" to "${c?.emoji ?: ""} ${c?.name ?: "Other"} · ${stats.topCategoryShare}%".trim()
        },
        stats.topPlace?.let { "Top place" to "$it · ${Money.format(stats.topPlacePaise)}" },
        stats.mostVisited?.takeIf { stats.mostVisitedTimes > 1 }?.let { "Most visits" to "$it · ${stats.mostVisitedTimes} times" },
        stats.biggest?.let { "Biggest payment" to "${Money.format(it.amountPaise)} · ${it.title ?: "someone"}" },
        "Payments of ₹30 or less" to "${stats.small}",
        stats.busiestWeekday?.let { "Biggest spending day" to it.getDisplayName(TextStyle.FULL, Locale.getDefault()) + "s" },
        "Days without spending" to "${stats.noSpendDays}",
        stats.lateNight.takeIf { it > 0 }?.let { "After 10 pm" to "$it ${if (it == 1) "payment" else "payments"}" },
    )
}
