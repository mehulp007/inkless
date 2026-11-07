package com.mehul.inkless.ui.wrapped

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mehul.inkless.R
import com.mehul.inkless.data.db.entity.CategoryEntity
import com.mehul.inkless.insights.WrappedStats
import com.mehul.inkless.ui.components.ChipTone
import com.mehul.inkless.ui.components.EmptyState
import com.mehul.inkless.ui.components.InklessCard
import com.mehul.inkless.ui.components.InklessTopBar
import com.mehul.inkless.ui.components.Overline
import com.mehul.inkless.ui.components.PageHeadline
import com.mehul.inkless.ui.components.PrimaryButton
import com.mehul.inkless.ui.components.SectionHeader
import com.mehul.inkless.ui.components.StatusChip
import com.mehul.inkless.ui.components.TintCard
import com.mehul.inkless.ui.format.Money
import com.mehul.inkless.ui.theme.Inkless
import com.mehul.inkless.ui.theme.Spacing
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.absoluteValue

/** A month in a few numbers, and a picture of them to share. */
@Composable
fun WrappedScreen(onBack: () -> Unit, viewModel: WrappedViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    WrappedContent(
        state = state,
        onBack = onBack,
        onPrevious = viewModel::previous,
        onNext = viewModel::next,
        onShare = { stats, lines ->
            WrappedImage.share(context, stats, lines.take(WrappedText.IMAGE_LINES), WrappedText.title(stats), Money.format(stats.totalPaise), WrappedText.sub(stats))
        },
    )
}

@Composable
fun WrappedContent(
    state: WrappedState,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShare: (WrappedStats, List<Pair<String, String>>) -> Unit,
) {
    val colors = Inkless.colors
    Surface(Modifier.fillMaxSize(), color = colors.background) {
        Column(Modifier.fillMaxSize()) {
            InklessTopBar("Wrapped", onBack = onBack)
            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevious) {
                    Icon(painterResource(R.drawable.icon_chevron_left), contentDescription = "Previous month", tint = colors.ink)
                }
                Text(
                    "${monthName(state.month)} ${state.month.year}",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.ink,
                    textAlign = TextAlign.Center,
                )
                IconButton(onClick = onNext, enabled = state.canGoForward) {
                    Icon(
                        painterResource(R.drawable.icon_chevron_right),
                        contentDescription = "Next month",
                        tint = if (state.canGoForward) colors.ink else colors.hairline,
                    )
                }
            }
            val stats = state.stats
            when {
                state.unavailable -> Message("Nothing to look back on", "Log in or open the demo, and your months show up here.")
                stats == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = colors.primary) }
                stats.payments == 0 -> Message("Nothing spent in ${monthName(stats.month)}", "Months with payments in them get their own numbers here.")
                else -> Month(stats, state.categories, onShare)
            }
        }
    }
}

@Composable
private fun Message(title: String, body: String) {
    Box(Modifier.fillMaxSize().padding(Spacing.gutter), Alignment.Center) {
        EmptyState(title = title, body = body, icon = R.drawable.icon_sparkles)
    }
}

@Composable
private fun Month(stats: WrappedStats, categories: Map<String, CategoryEntity>, onShare: (WrappedStats, List<Pair<String, String>>) -> Unit) {
    val colors = Inkless.colors
    val context = LocalContext.current
    val lines = WrappedText.lines(stats, categories)
    val picture = remember(stats, categories) {
        WrappedImage.render(context, lines.take(WrappedText.IMAGE_LINES), WrappedText.title(stats), Money.format(stats.totalPaise), WrappedText.sub(stats))
            .asImageBitmap()
    }
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = Spacing.gutter, vertical = Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        PageHeadline("${monthName(stats.month)}, in a few numbers.")
        TintCard {
            Overline("Spent")
            Text(Money.format(stats.totalPaise), style = Inkless.type.moneyLarge, color = colors.ink)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "over ${stats.payments} ${if (stats.payments == 1) "payment" else "payments"}",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                )
                WrappedText.change(stats)?.let { (text, more) -> StatusChip(text, tone = if (more) ChipTone.Check else ChipTone.Good) }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            lines.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    pair.forEach { (label, value) ->
                        InklessCard(Modifier.weight(1f)) {
                            Text(label, style = MaterialTheme.typography.bodySmall, color = colors.muted)
                            Text(value, Modifier.padding(top = Spacing.xs), style = MaterialTheme.typography.titleSmall, color = colors.ink)
                        }
                    }
                    if (pair.size == 1) Box(Modifier.weight(1f))
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            SectionHeader("The picture you'd share")
            Image(
                picture,
                contentDescription = "Picture of ${monthName(stats.month)} in numbers",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1080f / 1350f)
                    .clip(MaterialTheme.shapes.large)
                    .border(1.dp, colors.hairline, MaterialTheme.shapes.large),
            )
            Text(
                "Only these numbers are in it: no payment details, no account, nothing else.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted,
            )
        }
        PrimaryButton("Share as a picture", onClick = { onShare(stats, lines) }, icon = R.drawable.icon_share_2)
    }
}

private fun monthName(month: YearMonth) = month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())

/** The words on the Wrapped card, shared by the screen and the picture. */
object WrappedText {

    /** How many lines fit in the picture. */
    const val IMAGE_LINES = 5

    fun title(stats: WrappedStats) = "My ${monthName(stats.month)}"

    fun sub(stats: WrappedStats): String {
        val change = stats.change ?: return "over ${stats.payments} payments"
        val previous = monthName(stats.month.minusMonths(1))
        return when {
            change > 0 -> "over ${stats.payments} payments · $change% more than $previous"
            change < 0 -> "over ${stats.payments} payments · ${change.absoluteValue}% less than $previous"
            else -> "over ${stats.payments} payments · same as $previous"
        }
    }

    /** "12% more than September" and whether it's more; null with nothing to compare. */
    fun change(stats: WrappedStats): Pair<String, Boolean>? {
        val change = stats.change ?: return null
        val previous = monthName(stats.month.minusMonths(1))
        return when {
            change > 0 -> "$change% more than $previous" to true
            change < 0 -> "${change.absoluteValue}% less than $previous" to false
            else -> "Same as $previous" to false
        }
    }

    fun lines(stats: WrappedStats, categories: Map<String, CategoryEntity>): List<Pair<String, String>> = listOfNotNull(
        stats.topCategoryId?.let { id ->
            val c = categories[id]
            "Most went on" to "${c?.name ?: "Other"} · ${stats.topCategoryShare}%"
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
