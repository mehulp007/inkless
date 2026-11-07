package com.mehul.inkless.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mehul.inkless.R
import com.mehul.inkless.budget.BudgetSnapshot
import com.mehul.inkless.data.db.entity.CategoryEntity
import com.mehul.inkless.data.seed.CategoryIds
import com.mehul.inkless.parsing.Amounts
import com.mehul.inkless.ui.components.CategoryPicker
import com.mehul.inkless.ui.components.ChipTone
import com.mehul.inkless.ui.components.FieldNote
import com.mehul.inkless.ui.components.InklessBottomSheet
import com.mehul.inkless.ui.components.InklessTextField
import com.mehul.inkless.ui.components.PrimaryButton
import com.mehul.inkless.ui.components.StatusChip
import com.mehul.inkless.ui.components.TintCard
import com.mehul.inkless.ui.format.Money
import com.mehul.inkless.ui.theme.Inkless
import com.mehul.inkless.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** ₹10 lakh: anything bigger is a typo. */
private const val MAX_CASH_PAISE = 10_00_000_00L

/** Cash leaves no SMS, so it's typed in here: amount, category, and optionally where and why. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSheet(
    categories: List<CategoryEntity>,
    snapshot: BudgetSnapshot?,
    onAdd: (amountPaise: Long, categoryId: String, merchant: String?, note: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    InklessBottomSheet(onDismiss = onDismiss) {
        QuickAddForm(categories, snapshot, onAdd, onDismiss, today = LocalDate.now())
    }
}

/** The sheet's insides, separate so they can be tested and previewed without a sheet. */
@Composable
fun QuickAddForm(
    categories: List<CategoryEntity>,
    snapshot: BudgetSnapshot?,
    onAdd: (amountPaise: Long, categoryId: String, merchant: String?, note: String?) -> Unit,
    onClose: () -> Unit,
    today: LocalDate,
    focusAmount: Boolean = true,
) {
    var amountText by rememberSaveable { mutableStateOf("") }
    var categoryId by rememberSaveable { mutableStateOf(CategoryIds.FOOD_CANTEEN) }
    var merchant by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var picking by rememberSaveable { mutableStateOf(false) }
    val amount = Amounts.toPaise(amountText)?.takeIf { it in 1..MAX_CASH_PAISE }
    val focus = remember { FocusRequester() }
    val colors = Inkless.colors

    Column(
        Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("A little cash spent.", Modifier.weight(1f), style = MaterialTheme.typography.displaySmall, color = colors.ink)
            IconButton(onClick = onClose) {
                Icon(painterResource(R.drawable.icon_x), contentDescription = "Close", tint = colors.ink)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusChip("Not saved yet", tone = ChipTone.Good)
            Text(
                "Today · ${today.format(DAY_MONTH)}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted,
                textAlign = TextAlign.End,
            )
        }
        InklessTextField(
            value = amountText,
            onValueChange = { new -> amountText = new.filter { it.isDigit() || it == '.' }.take(10) },
            modifier = Modifier.focusRequester(focus),
            label = "Amount",
            prefix = "₹",
            money = true,
            note = if (amountText.isNotEmpty() && amount == null) "That doesn't look like an amount" else null,
            noteKind = if (amountText.isNotEmpty() && amount == null) FieldNote.Problem else FieldNote.Plain,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text("Category", style = MaterialTheme.typography.labelMedium, color = colors.muted)
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = colors.surface,
                border = BorderStroke(1.dp, colors.hairline),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable(role = Role.Button, onClickLabel = "Choose a category") { picking = !picking },
            ) {
                Row(Modifier.padding(Spacing.l), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        categories.firstOrNull { it.id == categoryId }?.name ?: "Choose a category",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.ink,
                    )
                    Icon(
                        painterResource(if (picking) R.drawable.icon_x else R.drawable.icon_chevron_down),
                        contentDescription = null,
                        tint = colors.muted,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            if (picking) {
                CategoryPicker(categories = categories, selectedId = categoryId, onSelect = { categoryId = it; picking = false })
            }
        }
        InklessTextField(
            value = merchant,
            onValueChange = { merchant = it.take(40) },
            label = "Merchant · optional",
            placeholder = "Block C Chai",
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        )
        InklessTextField(
            value = note,
            onValueChange = { note = it.take(60) },
            label = "Note · optional",
            placeholder = "Evening chai, paid in cash",
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        if (snapshot != null) {
            TintCard(padding = PaddingValues(horizontal = Spacing.l, vertical = Spacing.m)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Icon(painterResource(R.drawable.icon_info), contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
                    Text(safeAfter(snapshot, amount ?: 0), style = MaterialTheme.typography.bodyMedium, color = colors.primary)
                }
            }
        }
        PrimaryButton(
            "Save cash expense",
            onClick = { amount?.let { onAdd(it, categoryId, merchant.ifBlank { null }, note.ifBlank { null }) } },
            enabled = amount != null,
        )
    }

    if (focusAmount) LaunchedEffect(Unit) { focus.requestFocus() }
}

/** "Safe today after saving: ₹44", or how far over today's share it would go. */
internal fun safeAfter(snapshot: BudgetSnapshot, amountPaise: Long): String {
    val left = snapshot.safeToSpendPaise - amountPaise
    return if (left >= 0) "Safe today after saving: ${Money.format(left)}" else "Over today's share after saving: ${Money.format(-left)}"
}

private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM")
