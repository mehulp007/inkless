package com.mehul.inkless.ui.breakdown

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import com.mehul.inkless.data.db.entity.CategoryEntity
import com.mehul.inkless.ui.format.Money

/**
 * Splits one payment into items: type an amount, pick a category, next. The last item always
 * takes whatever is left, so the items can't help adding up to the payment.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreakdownScreen(
    onDone: () -> Unit,
    viewModel: BreakdownViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val txn = state.transaction

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(txn?.let { "Break down ${Money.format(it.amountPaise)}" } ?: "Break down") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading || txn == null) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                if (state.loading) CircularProgressIndicator() else Text("This payment is gone.")
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Each item counts in its own category. On Home the payment shows under its biggest item.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.fromShare) {
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.small) {
                    Text(
                        "Filled in from the order you shared. Check the amounts and categories, then save. " +
                            "Delivery, fees and discounts end up in the last item.",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }

            state.lines.forEachIndexed { index, line ->
                ItemCard(
                    title = "Item ${index + 1}",
                    categories = state.categories,
                    categoryId = line.categoryId,
                    note = line.note,
                    onCategory = { viewModel.setCategory(line.key, it) },
                    onNote = { viewModel.setNote(line.key, it) },
                    onRemove = if (state.lines.size > 1) ({ viewModel.removeLine(line.key) }) else null,
                ) {
                    OutlinedTextField(
                        value = line.amountText,
                        onValueChange = { viewModel.setAmount(line.key, it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Amount") },
                        prefix = { Text("₹") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
            }

            TextButton(onClick = viewModel::addLine) { Text("+ Add another item") }

            ItemCard(
                title = "Item ${state.lines.size + 1} · the rest",
                categories = state.categories,
                categoryId = state.lastCategoryId,
                note = state.lastNote,
                onCategory = viewModel::setLastCategory,
                onNote = viewModel::setLastNote,
                onRemove = null,
            ) {
                val rest = state.restPaise
                Text(
                    if (rest > 0) "${Money.format(rest, alwaysShowPaise = rest % 100 != 0L)} left" else "Nothing left",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (rest > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                )
            }

            state.problem?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = { viewModel.save(then = onDone) },
                enabled = state.problem == null,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text("Save breakdown") }
        }
    }
}

@Composable
private fun ItemCard(
    title: String,
    categories: List<CategoryEntity>,
    categoryId: String?,
    note: String,
    onCategory: (String) -> Unit,
    onNote: (String) -> Unit,
    onRemove: (() -> Unit)?,
    amount: @Composable () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                if (onRemove != null) {
                    IconButton(onClick = onRemove) {
                        Icon(painterResource(R.drawable.ic_delete), contentDescription = "Remove $title")
                    }
                }
            }
            amount()
            OutlinedTextField(
                value = note,
                onValueChange = onNote,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("What was it? (optional)") },
                singleLine = true,
            )
            // The picked chip may be scrolled out of sight, so say it in words too.
            val picked = categories.firstOrNull { it.id == categoryId }
            Text(
                picked?.name ?: "Pick a category",
                style = MaterialTheme.typography.labelLarge,
                color = if (picked != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // One scrolling row per item keeps a long breakdown readable.
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { category ->
                    FilterChip(
                        selected = category.id == categoryId,
                        onClick = { onCategory(category.id) },
                        label = { Text(category.name) },
                    )
                }
            }
        }
    }
}
