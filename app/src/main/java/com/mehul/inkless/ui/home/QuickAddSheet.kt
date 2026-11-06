package com.mehul.inkless.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mehul.inkless.data.db.entity.CategoryEntity
import com.mehul.inkless.data.seed.CategoryIds
import com.mehul.inkless.parsing.Amounts
import com.mehul.inkless.ui.components.CategoryPicker

private const val MAX_CASH_PAISE = 10_00_000_00L // ₹10 lakh: anything bigger is a typo

/** Bottom sheet for cash spends, which no SMS will ever tell us about. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSheet(
    categories: List<CategoryEntity>,
    onAdd: (amountPaise: Long, categoryId: String, note: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var amountText by rememberSaveable { mutableStateOf("") }
    var categoryId by rememberSaveable { mutableStateOf(CategoryIds.FOOD_CANTEEN) }
    var note by rememberSaveable { mutableStateOf("") }
    val amount = Amounts.toPaise(amountText)?.takeIf { it in 1..MAX_CASH_PAISE }
    val focus = remember { FocusRequester() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Add cash spend", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = amountText,
                onValueChange = { new -> amountText = new.filter { it.isDigit() || it == '.' }.take(10) },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                label = { Text("Amount") },
                prefix = { Text("₹") },
                singleLine = true,
                isError = amountText.isNotEmpty() && amount == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            CategoryPicker(categories = categories, selectedId = categoryId, onSelect = { categoryId = it })
            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(60) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("What was it? (optional)") },
                singleLine = true,
            )
            Button(
                onClick = { amount?.let { onAdd(it, categoryId, note) } },
                enabled = amount != null,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text("Add") }
        }
    }

    LaunchedEffect(Unit) { focus.requestFocus() }
}
