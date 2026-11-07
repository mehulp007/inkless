package com.mehul.inkless.ui.money

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mehul.inkless.R
import com.mehul.inkless.budget.BudgetSnapshot
import com.mehul.inkless.budget.BudgetText
import com.mehul.inkless.ui.budget.PaceChart
import com.mehul.inkless.ui.components.BudgetBar
import com.mehul.inkless.ui.components.InklessCard
import com.mehul.inkless.ui.components.InklessTopBar
import com.mehul.inkless.ui.components.NavRow
import com.mehul.inkless.ui.components.Overline
import com.mehul.inkless.ui.components.ProgressBar
import com.mehul.inkless.ui.components.SectionHeader
import com.mehul.inkless.ui.components.StatusChip
import com.mehul.inkless.ui.components.ChipTone
import com.mehul.inkless.ui.components.TextLink
import com.mehul.inkless.ui.components.TintCard
import com.mehul.inkless.ui.components.ToggleRow
import com.mehul.inkless.ui.format.Money
import com.mehul.inkless.ui.theme.Inkless
import com.mehul.inkless.ui.theme.Spacing
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** The Money tab: the allowance and how the month is going, category budgets, and the screens that look back. */
@Composable
fun MoneyScreen(
    onOpenBudget: () -> Unit,
    onOpenCategoryBudgets: () -> Unit,
    onOpenCategory: (String) -> Unit,
    onOpenInsights: () -> Unit,
    onOpenAsk: () -> Unit,
    onOpenWrapped: (YearMonth) -> Unit,
    viewModel: MoneyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val weekly by viewModel.weeklySummary.collectAsStateWithLifecycle()
    MoneyContent(
        state = state,
        weeklySummary = weekly,
        onWeeklySummary = viewModel::setWeeklySummary,
        onOpenBudget = onOpenBudget,
        onOpenCategoryBudgets = onOpenCategoryBudgets,
        onOpenCategory = onOpenCategory,
        onOpenInsights = onOpenInsights,
        onOpenAsk = onOpenAsk,
        onOpenWrapped = onOpenWrapped,
    )
}

@Composable
fun MoneyContent(
    state: MoneyState,
    weeklySummary: Boolean,
    onWeeklySummary: (Boolean) -> Unit,
    onOpenBudget: () -> Unit,
    onOpenCategoryBudgets: () -> Unit,
    onOpenCategory: (String) -> Unit,
    onOpenInsights: () -> Unit,
    onOpenAsk: () -> Unit,
    onOpenWrapped: (YearMonth) -> Unit,
) {
    val colors = Inkless.colors
    Surface(Modifier.fillMaxSize(), color = colors.background) {
        Column(Modifier.fillMaxSize()) {
            InklessTopBar("Money", icon = R.drawable.icon_chart_trend)
            if (state.loading) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = colors.primary) }
                return@Column
            }
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.gutter, vertical = Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.xl),
            ) {
                val snapshot = state.snapshot
                if (snapshot == null) {
                    TintCard(onClick = onOpenBudget) {
                        Text("Set your monthly allowance", style = MaterialTheme.typography.titleMedium, color = colors.ink)
                        Text(
                            "Then this page shows what's left, the pace you're spending at, and what's safe to spend each day.",
                            Modifier.padding(top = Spacing.xs),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.muted,
                        )
                        TextLink("Set it up →", onClick = onOpenBudget)
                    }
                } else {
                    Allowance(snapshot, onEdit = onOpenBudget)
                    PaceCard(snapshot)
                    TintCard {
                        Text("How today's ${BudgetText.safeToSpendAmount(snapshot)} is worked out", style = MaterialTheme.typography.titleMedium, color = colors.ink)
                        Text(BudgetText.formula(snapshot), Modifier.padding(top = Spacing.s), style = MaterialTheme.typography.titleSmall, color = colors.primary)
                        Text(
                            "Allowance minus spending before today, shared over the days left including today, minus today's spend.",
                            Modifier.padding(top = Spacing.s),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.muted,
                        )
                    }
                }

                CategoryBudgets(state, onManage = onOpenCategoryBudgets, onOpenCategory = onOpenCategory)

                if (snapshot != null) {
                    val cap = snapshot.dailyFoodCapPaise
                    InklessCard(onClick = onOpenBudget) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    if (cap != null) "Daily food cap · ${Money.format(cap)}" else "Daily food cap",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colors.ink,
                                )
                                Text(
                                    if (cap != null) foodLine(snapshot.foodTodayPaise, cap) else "Off · a nudge for canteen and delivery, not a limit",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.muted,
                                )
                                if (cap != null) Text("Canteen + delivery combined", style = MaterialTheme.typography.bodySmall, color = colors.muted)
                            }
                            StatusChip(if (cap != null) "On" else "Off", tone = if (cap != null) ChipTone.Good else ChipTone.Neutral)
                        }
                    }
                }

                InklessCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Overline("Semester · ${state.semesterName}", Modifier.weight(1f))
                    }
                    Text(Money.format(state.semesterPaise), style = Inkless.type.moneyMedium, color = colors.ink)
                    HorizontalDivider(Modifier.padding(vertical = Spacing.m), color = colors.hairline)
                    ToggleRow(
                        title = "Weekly summary · Sundays",
                        subtitle = "The week's spending and what's left, as a notification",
                        checked = weeklySummary,
                        onCheckedChange = onWeeklySummary,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    SectionHeader("Look back")
                    InklessCard(padding = PaddingValues(vertical = Spacing.xs)) {
                        NavRow("Insights", subtitle = "Spending alerts, what's coming up, how well Inkless guesses", icon = R.drawable.icon_lightbulb, onClick = onOpenInsights)
                        HorizontalDivider(Modifier.padding(horizontal = Spacing.l), color = colors.hairline)
                        NavRow("Ask Inkless", subtitle = "\"How much on food last month?\"", icon = R.drawable.icon_message_question, onClick = onOpenAsk)
                        HorizontalDivider(Modifier.padding(horizontal = Spacing.l), color = colors.hairline)
                        NavRow(
                            "${state.wrappedMonth.format(MONTH)}, wrapped",
                            subtitle = "The month in a few numbers, with a picture to share",
                            icon = R.drawable.icon_sparkles,
                            onClick = { onOpenWrapped(state.wrappedMonth) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Allowance(snapshot: BudgetSnapshot, onEdit: () -> Unit) {
    val colors = Inkless.colors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Overline("Your allowance cycle")
                Text(BudgetText.cycleRange(snapshot), style = MaterialTheme.typography.titleLarge, color = colors.ink)
            }
            TextLink("Edit", onClick = onEdit)
        }
        Text(
            // Whole rupees, rounded down, like the safe-to-spend figure: never more than is there.
            Money.format(snapshot.remainingPaise.coerceAtLeast(0) / 100 * 100),
            Modifier.padding(top = Spacing.s),
            style = Inkless.type.moneyLarge,
            color = if (snapshot.remainingPaise < 0) colors.danger else colors.ink,
        )
        Text(BudgetText.leftOfAllowance(snapshot), style = MaterialTheme.typography.bodyMedium, color = colors.muted)
        ProgressBar(fraction = snapshot.usedFraction, color = if (snapshot.usedFraction > 1f) colors.danger else colors.primary)
        Row {
            Text(BudgetText.netSpending(snapshot), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = colors.muted)
            Text(BudgetText.nextArrival(snapshot), style = MaterialTheme.typography.bodySmall, color = colors.muted)
        }
    }
}

@Composable
private fun PaceCard(snapshot: BudgetSnapshot) {
    val colors = Inkless.colors
    InklessCard {
        Text("Keep the month in view", style = MaterialTheme.typography.titleMedium, color = colors.ink)
        Row(Modifier.padding(top = Spacing.s), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
            Legend(colors.primary, "Spent + forecast")
            Legend(colors.muted, "Even pace")
        }
        PaceChart(
            cumulativePaise = snapshot.cumulativePaise,
            budgetPaise = snapshot.budgetPaise,
            cycleDays = snapshot.cycle.days,
            pacePaisePerDay = snapshot.pacePaisePerDay,
            label = BudgetText.todayTag(snapshot),
            modifier = Modifier.fillMaxWidth().height(150.dp).padding(top = Spacing.l),
        )
        Row(Modifier.fillMaxWidth().padding(top = Spacing.s)) {
            val start = snapshot.cycle.start
            val last = snapshot.cycle.endExclusive.minusDays(1)
            Text(start.format(DAY), style = MaterialTheme.typography.labelSmall, color = colors.muted)
            Text(start.plusDays((snapshot.cycle.days / 2).toLong()).format(DAY), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = colors.muted, textAlign = TextAlign.Center)
            Text(last.format(DAY), style = MaterialTheme.typography.labelSmall, color = colors.muted)
        }
        Text(BudgetText.paceVsPlan(snapshot), Modifier.padding(top = Spacing.m), style = MaterialTheme.typography.bodySmall, color = colors.muted)
        Text(BudgetText.runsOutLine(snapshot), style = MaterialTheme.typography.bodySmall, color = colors.muted)
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Surface(Modifier.width(12.dp).height(3.dp), color = color) {}
        Text(label, style = MaterialTheme.typography.labelSmall, color = Inkless.colors.muted)
    }
}

@Composable
private fun CategoryBudgets(state: MoneyState, onManage: () -> Unit, onOpenCategory: (String) -> Unit) {
    val colors = Inkless.colors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        SectionHeader("${state.month.format(MONTH)} category budgets", action = "Manage", onAction = onManage)
        if (state.budgets.isEmpty()) {
            Text(
                "No category budgets yet. Give a category a monthly amount and its bar shows up here.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted,
            )
        }
        state.budgets.forEach { item ->
            Column(
                Modifier.fillMaxWidth().clickable(onClickLabel = "Open ${item.category.name}") { onOpenCategory(item.category.id) }.padding(vertical = Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.category.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = colors.ink)
                    Text(
                        "${Money.format(item.spentPaise.coerceAtLeast(0))} / ${Money.format(item.budgetPaise)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (item.spentPaise > item.budgetPaise) colors.danger else colors.muted,
                    )
                }
                BudgetBar(item.spentPaise, item.budgetPaise, height = 6.dp)
            }
        }
        if (state.budgets.isNotEmpty()) {
            Text("Calendar-month budgets. Refunds count back against their category.", style = MaterialTheme.typography.bodySmall, color = colors.muted)
        }
    }
}

/** "₹120 today · ₹80 to your cap", or how far over it today is. */
internal fun foodLine(todayPaise: Long, capPaise: Long): String {
    val left = capPaise - todayPaise.coerceAtLeast(0)
    return "${Money.format(todayPaise.coerceAtLeast(0))} today · " +
        if (left >= 0) "${Money.format(left)} to your cap" else "${Money.format(-left)} over your cap"
}

private val MONTH = DateTimeFormatter.ofPattern("MMMM")
private val DAY = DateTimeFormatter.ofPattern("d MMM")
