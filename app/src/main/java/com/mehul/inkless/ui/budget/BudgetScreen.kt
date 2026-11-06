package com.mehul.inkless.ui.budget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mehul.inkless.R
import com.mehul.inkless.ui.format.Money

/** The monthly allowance, the day it arrives and a daily food cap: everything the budget works from. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(
    onBack: () -> Unit,
    viewModel: BudgetViewModel = hiltViewModel(),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val semesterSpent by viewModel.semesterSpentPaise.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Budget") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (form.loading) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "What you live on each month. Inkless spreads what's left over the days until the next " +
                    "allowance, so Home can say what you can spend today.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = form.amountText,
                onValueChange = viewModel::setAmount,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Monthly allowance") },
                prefix = { Text("₹") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            OutlinedTextField(
                value = form.dayText,
                onValueChange = viewModel::setDay,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Usually arrives on day") },
                supportingText = { Text("1 to 31. A short month uses its last day.") },
                isError = form.dayText.isNotEmpty() && form.day == null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                value = form.foodCapText,
                onValueChange = viewModel::setFoodCap,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Daily food cap (optional)") },
                prefix = { Text("₹") },
                supportingText = { Text("Canteen and food delivery a day. Days over it count as mess skipped.") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Weekly summary", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "A notification on Sunday evening: the week's spending and what's left",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = form.weeklySummary, onCheckedChange = viewModel::setWeeklySummary)
            }
            Button(
                onClick = { viewModel.save(then = onBack) },
                enabled = form.canSave,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text("Save") }
            if (form.hasBudget) {
                TextButton(onClick = { viewModel.remove(then = onBack) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Stop budgeting", color = MaterialTheme.colorScheme.error)
                }
            }

            HorizontalDivider()
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("This semester", style = MaterialTheme.typography.titleSmall)
                    Text(viewModel.semesterName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(Money.format(semesterSpent.coerceAtLeast(0)), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
