package com.mehul.inkless.ui.category

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mehul.inkless.R
import com.mehul.inkless.parsing.Amounts
import com.mehul.inkless.ui.budget.MonthBars
import com.mehul.inkless.ui.format.Money
import com.mehul.inkless.ui.theme.Inkless
import java.time.format.DateTimeFormatter

/** One category: this month against its budget, the last six months, and where the money went. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(
    onBack: () -> Unit,
    viewModel: CategoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editingBudget by rememberSaveable { mutableStateOf(false) }
    val category = state.category

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(category?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading || category == null) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val budget = category.monthlyBudgetPaise
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                Text("This month", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Money.format(state.thisMonthPaise.coerceAtLeast(0)), style = Inkless.type.moneyMedium)
            }

            if (budget != null) {
                val fraction = (state.thisMonthPaise.toFloat() / budget).coerceAtLeast(0f)
                LinearProgressIndicator(
                    progress = { fraction.coerceAtMost(1f) },
                    modifier = Modifier.fillMaxWidth().height(10.dp),
                    color = if (fraction > 1f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val left = budget - state.thisMonthPaise
                    Text(
                        if (left >= 0) "${Money.format(left)} left of ${Money.format(budget)}" else "${Money.format(-left)} over the ${Money.format(budget)} budget",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (left >= 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = { editingBudget = true }) { Text("Change") }
                }
            } else {
                TextButton(onClick = { editingBudget = true }) { Text("Set a monthly budget for this category") }
            }

            HorizontalDivider()
            Text("Last 6 months", style = MaterialTheme.typography.titleSmall)
            MonthBars(state.months.map { it.second }, budget, Modifier.fillMaxWidth().height(120.dp))
            Row(Modifier.fillMaxWidth()) {
                state.months.forEach { (month, paise) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(month.format(MONTH), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(Money.short(paise), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                    }
                }
            }

            HorizontalDivider()
            Text("Top this month", style = MaterialTheme.typography.titleSmall)
            if (state.topMerchants.isEmpty()) {
                Text("Nothing yet this month.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.topMerchants.forEach { merchant ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(merchant.merchantName ?: "Cash and others", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (merchant.count == 1) "1 payment" else "${merchant.count} payments",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(Money.format(merchant.totalPaise), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                }
            }
        }
    }

    if (editingBudget && category != null) {
        BudgetDialog(
            current = category.monthlyBudgetPaise,
            onSave = { viewModel.setBudget(it); editingBudget = false },
            onDismiss = { editingBudget = false },
        )
    }
}

@Composable
private fun BudgetDialog(current: Long?, onSave: (Long?) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(current?.let { (it / 100).toString() }.orEmpty()) }
    val paise = Amounts.toPaise(text)?.takeIf { it > 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Monthly budget") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { new -> text = new.filter { it.isDigit() || it == '.' }.take(9) },
                prefix = { Text("₹") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(paise) }, enabled = paise != null) { Text("Save") } },
        dismissButton = {
            if (current != null) {
                TextButton(onClick = { onSave(null) }) { Text("Remove budget") }
            } else {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

private val MONTH = DateTimeFormatter.ofPattern("MMM")
