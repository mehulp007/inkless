package com.mehul.inkless.ui.budget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mehul.inkless.data.repo.LedgerRepository
import com.mehul.inkless.parsing.Amounts
import com.mehul.inkless.ui.components.BudgetBar
import com.mehul.inkless.ui.components.CategoryTile
import com.mehul.inkless.ui.components.InklessBottomSheet
import com.mehul.inkless.ui.components.InklessCard
import com.mehul.inkless.ui.components.InklessTextField
import com.mehul.inkless.ui.components.InklessTopBar
import com.mehul.inkless.ui.components.LedgerRow
import com.mehul.inkless.ui.components.PageHeadline
import com.mehul.inkless.ui.components.PrimaryButton
import com.mehul.inkless.ui.components.TextLink
import com.mehul.inkless.ui.format.Money
import com.mehul.inkless.ui.money.CategoryBudget
import com.mehul.inkless.ui.theme.Inkless
import com.mehul.inkless.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class CategoryBudgetsViewModel @Inject constructor(
    private val repository: LedgerRepository,
    private val clock: Clock,
) : ViewModel() {

    private val month = YearMonth.now(clock)
    private fun millis(m: YearMonth) = m.atDay(1).atStartOfDay(clock.zone).toInstant().toEpochMilli()

    /** Every category with what it took this month; the ones with a budget have it on [CategoryBudget.category]. */
    val items: StateFlow<List<CategoryBudget>?> = combine(
        repository.categories(),
        repository.categoryTotalsBetween(millis(month), millis(month.plusMonths(1))),
    ) { categories, totals ->
        val spent = totals.associate { it.categoryId to it.totalPaise }
        categories.map { CategoryBudget(it, spent[it.id] ?: 0) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Null clears it. */
    fun setBudget(categoryId: String, paise: Long?) {
        viewModelScope.launch { repository.setCategoryBudget(categoryId, paise) }
    }
}

/** Every category, its monthly budget if it has one, and what it took this month. */
@Composable
fun CategoryBudgetsScreen(onBack: () -> Unit, viewModel: CategoryBudgetsViewModel = hiltViewModel()) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    CategoryBudgetsContent(items, onBack, viewModel::setBudget)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryBudgetsContent(items: List<CategoryBudget>?, onBack: () -> Unit, onSetBudget: (String, Long?) -> Unit) {
    val colors = Inkless.colors
    var editing by rememberSaveable { mutableStateOf<String?>(null) }

    Surface(Modifier.fillMaxSize(), color = colors.background) {
        Column(Modifier.fillMaxSize()) {
            InklessTopBar("Category budgets", onBack = onBack)
            val list = items ?: return@Column
            LazyColumn(
                contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.s, bottom = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.l),
            ) {
                item(key = "headline") {
                    PageHeadline(
                        "A little room for each.",
                        lead = "A monthly amount per category, by calendar month. Going over never blocks anything; the bar just turns red.",
                    )
                }
                item(key = "list") {
                    InklessCard(padding = PaddingValues(horizontal = Spacing.l, vertical = Spacing.xs)) {
                        list.forEachIndexed { index, item ->
                            if (index > 0) HorizontalDivider(color = colors.hairline)
                            val budget = item.category.monthlyBudgetPaise
                            Column(Modifier.fillMaxWidth()) {
                                LedgerRow(
                                    title = item.category.name,
                                    amount = budget?.let(Money::format) ?: "Set",
                                    meta = if (budget != null) "${Money.format(item.spentPaise.coerceAtLeast(0))} this month" else "No budget",
                                    amountColor = if (budget != null) colors.ink else colors.primary,
                                    leading = { CategoryTile(item.category.id, size = 36.dp) },
                                    onClick = { editing = item.category.id },
                                )
                                if (budget != null) {
                                    BudgetBar(item.spentPaise, budget, height = 6.dp)
                                    Box(Modifier.fillMaxWidth().padding(bottom = Spacing.m))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val item = editing?.let { id -> items?.firstOrNull { it.category.id == id } }
    if (item != null) {
        var text by rememberSaveable(item.category.id) { mutableStateOf(item.category.monthlyBudgetPaise?.let { (it / 100).toString() }.orEmpty()) }
        val paise = Amounts.toPaise(text)?.takeIf { it > 0 }
        InklessBottomSheet(onDismiss = { editing = null }) {
            Text("${item.category.name} budget", style = MaterialTheme.typography.displaySmall, color = colors.ink)
            InklessTextField(
                value = text,
                onValueChange = { new -> text = new.filter { it.isDigit() || it == '.' }.take(9) },
                label = "Each calendar month",
                prefix = "₹",
                money = true,
                note = "${Money.format(item.spentPaise.coerceAtLeast(0))} spent so far this month.",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            PrimaryButton("Save budget", onClick = { onSetBudget(item.category.id, paise); editing = null }, enabled = paise != null)
            if (item.category.monthlyBudgetPaise != null) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextLink("Remove the budget", onClick = { onSetBudget(item.category.id, null); editing = null }, color = colors.danger)
                }
            }
        }
    }
}
