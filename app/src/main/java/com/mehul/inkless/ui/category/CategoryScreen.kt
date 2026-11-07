package com.mehul.inkless.ui.category

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mehul.inkless.data.db.entity.CaptureSource
import com.mehul.inkless.parsing.Amounts
import com.mehul.inkless.ui.budget.MonthBars
import com.mehul.inkless.ui.components.BudgetBar
import com.mehul.inkless.ui.components.CategoryTile
import com.mehul.inkless.ui.components.ChipTone
import com.mehul.inkless.ui.components.InklessBottomSheet
import com.mehul.inkless.ui.components.InklessCard
import com.mehul.inkless.ui.components.InklessTextField
import com.mehul.inkless.ui.components.InklessTopBar
import com.mehul.inkless.ui.components.LedgerRow
import com.mehul.inkless.ui.components.PrimaryButton
import com.mehul.inkless.ui.components.ProgressBar
import com.mehul.inkless.ui.components.SectionHeader
import com.mehul.inkless.ui.components.StatusChip
import com.mehul.inkless.ui.components.TextLink
import com.mehul.inkless.ui.format.Money
import com.mehul.inkless.ui.theme.Inkless
import com.mehul.inkless.ui.theme.Spacing
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** One category: this month against its budget, the last six months, where the money went, and today. */
@Composable
fun CategoryScreen(
    onBack: () -> Unit,
    onOpenTransaction: (String) -> Unit,
    viewModel: CategoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CategoryContent(
        state = state,
        month = viewModel.month,
        onBack = onBack,
        onSetBudget = viewModel::setBudget,
        onOpenTransaction = onOpenTransaction,
    )
}

@Composable
fun CategoryContent(
    state: CategoryDetail,
    month: YearMonth,
    onBack: () -> Unit,
    onSetBudget: (Long?) -> Unit,
    onOpenTransaction: (String) -> Unit,
) {
    var editingBudget by rememberSaveable { mutableStateOf(false) }
    val category = state.category
    val colors = Inkless.colors

    Surface(Modifier.fillMaxSize(), color = colors.background) {
        Column(Modifier.fillMaxSize()) {
            InklessTopBar(category?.name.orEmpty(), onBack = onBack)
            if (state.loading || category == null) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = colors.primary) }
                return@Column
            }
            val budget = category.monthlyBudgetPaise
            val spent = state.thisMonthPaise.coerceAtLeast(0)
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = Spacing.gutter, vertical = Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.xl),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Text(monthRange(month), style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                    Text(Money.format(spent), style = Inkless.type.moneyLarge, color = colors.ink)
                    if (budget != null) {
                        val fraction = spent.toFloat() / budget
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "of ${Money.format(budget)} monthly budget",
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.muted,
                            )
                            StatusChip("${(fraction * 100).toInt()}% used", tone = if (fraction > 1f) ChipTone.Danger else ChipTone.Good)
                        }
                        BudgetBar(spent, budget)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val left = budget - spent
                            Text(
                                if (left >= 0) "${Money.format(left)} left this calendar month · " else "${Money.format(-left)} over this month · ",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (left >= 0) colors.primary else colors.danger,
                            )
                            TextLink("Edit budget →", onClick = { editingBudget = true })
                        }
                    } else {
                        TextLink("Set a monthly budget →", onClick = { editingBudget = true })
                    }
                }

                InklessCard {
                    Text("The last six months", style = MaterialTheme.typography.titleMedium, color = colors.ink)
                    Row(Modifier.fillMaxWidth().padding(top = Spacing.l)) {
                        state.months.forEachIndexed { index, (_, paise) ->
                            Text(
                                Money.short(paise),
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (index == state.months.lastIndex) colors.primary else colors.muted,
                                fontWeight = if (index == state.months.lastIndex) FontWeight.SemiBold else null,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    MonthBars(state.months.map { it.second }, budget, Modifier.fillMaxWidth().height(110.dp).padding(top = Spacing.xs))
                    Row(Modifier.fillMaxWidth().padding(top = Spacing.s)) {
                        state.months.forEach { (m, _) ->
                            Text(m.format(SHORT_MONTH), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = colors.muted, textAlign = TextAlign.Center)
                        }
                    }
                    Text(
                        "${month.format(LONG_MONTH)} is in progress · totals count only ${category.name} items, after refunds.",
                        Modifier.padding(top = Spacing.m),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionHeader("Your usual places", Modifier.weight(1f))
                        Text("This month", style = MaterialTheme.typography.labelLarge, color = colors.primary)
                    }
                    if (state.topMerchants.isEmpty()) {
                        Text("Nothing yet this month.", style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                    }
                    val most = state.topMerchants.maxOfOrNull { it.totalPaise }?.coerceAtLeast(1) ?: 1
                    state.topMerchants.forEach { merchant ->
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(merchant.merchantName ?: "Cash and others", style = MaterialTheme.typography.bodyLarge, color = colors.ink)
                                    Text(
                                        if (merchant.count == 1) "1 visit" else "${merchant.count} visits",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.muted,
                                    )
                                }
                                Text(Money.format(merchant.totalPaise), style = Inkless.type.moneyRow, color = colors.ink)
                            }
                            ProgressBar(
                                fraction = merchant.totalPaise.toFloat() / most,
                                height = 4.dp,
                                track = colors.background,
                            )
                        }
                    }
                }

                if (state.today.isNotEmpty()) {
                    Column {
                        SectionHeader("Today's stops")
                        val zone = ZoneId.systemDefault()
                        state.today.forEach { stop ->
                            LedgerRow(
                                title = stop.title,
                                amount = if (stop.isRefund) "+${Money.format(stop.amountPaise)}" else Money.format(stop.amountPaise),
                                meta = "${Instant.ofEpochMilli(stop.timestamp).atZone(zone).format(TIME)} · ${sourcesLabel(stop.sources)}",
                                amountColor = if (stop.isRefund) colors.credit else colors.ink,
                                leading = { CategoryTile(category.id) },
                                onClick = { onOpenTransaction(stop.id) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (editingBudget && category != null) {
        BudgetSheet(
            categoryName = category.name,
            current = category.monthlyBudgetPaise,
            onSave = { onSetBudget(it); editingBudget = false },
            onDismiss = { editingBudget = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetSheet(categoryName: String, current: Long?, onSave: (Long?) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(current?.let { (it / 100).toString() }.orEmpty()) }
    val paise = Amounts.toPaise(text)?.takeIf { it > 0 }
    InklessBottomSheet(onDismiss = onDismiss) {
        Text("$categoryName budget", style = MaterialTheme.typography.displaySmall, color = Inkless.colors.ink)
        InklessTextField(
            value = text,
            onValueChange = { new -> text = new.filter { it.isDigit() || it == '.' }.take(9) },
            label = "Each calendar month",
            prefix = "₹",
            money = true,
            note = "Shown as a bar on this page. Going over never blocks anything.",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        PrimaryButton("Save budget", onClick = { onSave(paise) }, enabled = paise != null)
        if (current != null) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextLink("Remove the budget", onClick = { onSave(null) }, color = Inkless.colors.danger)
            }
        }
    }
}

/** "Bank SMS + notification", "Cash", … */
internal fun sourcesLabel(sources: Set<CaptureSource>): String {
    val names = sources.sortedBy { it.ordinal }.map {
        when (it) {
            CaptureSource.SMS -> "Bank SMS"
            CaptureSource.NOTIFICATION -> "notification"
            CaptureSource.MANUAL -> "Cash"
            CaptureSource.SCAN_PAY -> "Scan & pay"
        }
    }
    return names.joinToString(" + ").replaceFirstChar { it.uppercase() }.ifEmpty { "Added by hand" }
}

/** "1–30 November 2026" */
internal fun monthRange(month: YearMonth): String = "1–${month.lengthOfMonth()} ${month.format(LONG_MONTH_YEAR)}"

private val SHORT_MONTH = DateTimeFormatter.ofPattern("MMM")
private val LONG_MONTH = DateTimeFormatter.ofPattern("MMMM")
private val LONG_MONTH_YEAR = DateTimeFormatter.ofPattern("MMMM yyyy")
private val TIME = DateTimeFormatter.ofPattern("HH:mm")
