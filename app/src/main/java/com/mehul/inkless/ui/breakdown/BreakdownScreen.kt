package com.mehul.inkless.ui.breakdown

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mehul.inkless.R
import com.mehul.inkless.data.db.entity.CategoryEntity
import com.mehul.inkless.ui.components.CategoryIcons
import com.mehul.inkless.ui.components.CategoryPicker
import com.mehul.inkless.ui.components.CategoryTile
import com.mehul.inkless.ui.components.InklessBottomSheet
import com.mehul.inkless.ui.components.InklessCard
import com.mehul.inkless.ui.components.InklessTopBar
import com.mehul.inkless.ui.components.Overline
import com.mehul.inkless.ui.components.PrimaryButton
import com.mehul.inkless.ui.components.SecondaryButton
import com.mehul.inkless.ui.components.TintCard
import com.mehul.inkless.ui.components.WarmCard
import com.mehul.inkless.ui.format.Money
import com.mehul.inkless.ui.theme.Inkless
import com.mehul.inkless.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Splits one payment into items: a name, an amount and a category each. The last item always
 * takes whatever is left, so the items can't help adding up to the payment.
 */
@Composable
fun BreakdownScreen(
    onDone: () -> Unit,
    viewModel: BreakdownViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BreakdownContent(
        state = state,
        onBack = onDone,
        onAmount = viewModel::setAmount,
        onCategory = viewModel::setCategory,
        onName = viewModel::setNote,
        onAdd = viewModel::addLine,
        onRemove = viewModel::removeLine,
        onLastCategory = viewModel::setLastCategory,
        onLastName = viewModel::setLastNote,
        onSave = { viewModel.save(then = onDone) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreakdownContent(
    state: BreakdownState,
    onBack: () -> Unit,
    onAmount: (key: Int, text: String) -> Unit,
    onCategory: (key: Int, categoryId: String) -> Unit,
    onName: (key: Int, name: String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (key: Int) -> Unit,
    onLastCategory: (String) -> Unit,
    onLastName: (String) -> Unit,
    onSave: () -> Unit,
) {
    val colors = Inkless.colors
    val txn = state.transaction
    // Which item's category is being picked: a line's key, or LAST for the item taking the rest.
    var picking by rememberSaveable { mutableStateOf<Int?>(null) }

    Surface(Modifier.fillMaxSize(), color = colors.background) {
        Column(Modifier.fillMaxSize()) {
            InklessTopBar("Break down", onBack = onBack)
            if (state.loading || txn == null) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    if (state.loading) CircularProgressIndicator(color = colors.primary) else Text("This payment is gone.", color = colors.muted)
                }
                return@Column
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = Spacing.gutter, vertical = Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.l),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    CategoryTile(txn.categoryId)
                    Column(Modifier.weight(1f)) {
                        Text(state.title ?: txn.payeeRaw ?: txn.vpa ?: "Payment", style = MaterialTheme.typography.titleMedium, color = colors.ink)
                        Text(
                            "${Instant.ofEpochMilli(txn.timestamp).atZone(ZoneId.systemDefault()).format(DAY)} · One payment",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.muted,
                        )
                    }
                    Text(Money.format(txn.amountPaise), style = Inkless.type.moneyRow.copy(fontSize = 22.sp), color = colors.ink)
                }
                Text(
                    "Give every item a name and a category. The last one takes whatever is left, so the items always add up to the payment.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.muted,
                )
                if (state.fromShare) {
                    TintCard {
                        Text(
                            "Filled in from the order you shared. Check the amounts and categories, then save. " +
                                "Delivery, fees and discounts end up in the last item.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.ink,
                        )
                    }
                }

                state.lines.forEach { line ->
                    ItemCard(
                        name = line.note,
                        onName = { onName(line.key, it) },
                        category = state.categories.firstOrNull { it.id == line.categoryId },
                        onPickCategory = { picking = line.key },
                        onRemove = if (state.lines.size > 1) ({ onRemove(line.key) }) else null,
                    ) {
                        InlineField(
                            label = "Amount",
                            value = line.amountText,
                            onValueChange = { onAmount(line.key, it) },
                            placeholder = "0",
                            prefix = "₹",
                            alignEnd = true,
                            keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                    }
                }
                ItemCard(
                    name = state.lastNote,
                    onName = onLastName,
                    category = state.categories.firstOrNull { it.id == state.lastCategoryId },
                    onPickCategory = { picking = LAST },
                    onRemove = null,
                ) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                        Overline("The rest")
                        Text(
                            Money.format(state.restPaise.coerceAtLeast(0), alwaysShowPaise = true),
                            Modifier.padding(top = Spacing.xs),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (state.restPaise > 0) colors.ink else colors.danger,
                        )
                    }
                }

                SecondaryButton("Add item", onClick = onAdd, icon = R.drawable.icon_plus)

                val problem = state.problem
                if (problem == null) {
                    TintCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Items total", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = colors.ink)
                            Text(Money.format(txn.amountPaise, alwaysShowPaise = true), style = Inkless.type.moneyRow.copy(fontSize = 22.sp), color = colors.ink)
                        }
                        Row(Modifier.padding(top = Spacing.s), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            Icon(painterResource(R.drawable.icon_circle_check), contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
                            Text("₹0.00 left · matches payment exactly", style = MaterialTheme.typography.bodySmall, color = colors.primary)
                        }
                    }
                } else {
                    WarmCard { Text(problem, style = MaterialTheme.typography.bodyMedium) }
                }
                PrimaryButton("Save breakdown", onClick = onSave, enabled = problem == null)
            }
        }
    }

    picking?.let { key ->
        InklessBottomSheet(onDismiss = { picking = null }) {
            Text("Which category?", style = MaterialTheme.typography.titleLarge, color = colors.ink)
            CategoryPicker(
                categories = state.categories,
                selectedId = if (key == LAST) state.lastCategoryId else state.lines.firstOrNull { it.key == key }?.categoryId,
                onSelect = {
                    if (key == LAST) onLastCategory(it) else onCategory(key, it)
                    picking = null
                },
            )
        }
    }
}

private const val LAST = -1

@Composable
private fun ItemCard(
    name: String,
    onName: (String) -> Unit,
    category: CategoryEntity?,
    onPickCategory: () -> Unit,
    onRemove: (() -> Unit)?,
    amount: @Composable () -> Unit,
) {
    val colors = Inkless.colors
    InklessCard(padding = PaddingValues(start = Spacing.l, end = Spacing.s, top = Spacing.m, bottom = Spacing.l)) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.weight(1f)) {
                InlineField(
                    label = "Item name",
                    value = name,
                    onValueChange = onName,
                    placeholder = "What was it?",
                    keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            }
            Box(Modifier.width(110.dp)) { amount() }
            if (onRemove != null) {
                IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
                    Icon(painterResource(R.drawable.icon_x), contentDescription = "Remove ${name.ifBlank { "this item" }}", tint = colors.muted, modifier = Modifier.size(18.dp))
                }
            } else {
                Box(Modifier.width(40.dp))
            }
        }
        HorizontalDivider(Modifier.padding(top = Spacing.m, bottom = Spacing.m, end = Spacing.s), color = colors.hairline)
        Surface(
            shape = CircleShape,
            color = colors.tint,
            contentColor = colors.primary,
            modifier = Modifier
                .padding(end = Spacing.s)
                .fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = "Choose a category", onClick = onPickCategory),
        ) {
            Row(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Icon(painterResource(CategoryIcons.forCategory(category?.id)), contentDescription = null, modifier = Modifier.size(18.dp))
                Text(category?.name ?: "Choose a category", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                Icon(painterResource(R.drawable.icon_chevron_down), contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** A small label over borderless text: the way an item's name and amount sit inside its card. */
@Composable
private fun InlineField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboard: KeyboardOptions,
    prefix: String? = null,
    alignEnd: Boolean = false,
) {
    val colors = Inkless.colors
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Overline(label)
        val style = MaterialTheme.typography.bodyLarge.copy(color = colors.ink, textAlign = if (alignEnd) TextAlign.End else TextAlign.Start)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs).semantics { contentDescription = label },
            textStyle = style,
            singleLine = true,
            keyboardOptions = keyboard,
            cursorBrush = SolidColor(colors.primary),
            // The ₹ is drawn as part of the text, so it stays right next to the amount.
            visualTransformation = prefix?.let(::PrefixTransformation) ?: VisualTransformation.None,
            decorationBox = { inner ->
                Box(contentAlignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart) {
                    if (value.isEmpty()) Text((prefix.orEmpty()) + placeholder, Modifier.fillMaxWidth(), style = style.copy(color = colors.muted))
                    inner()
                }
            },
        )
    }
}

private val DAY = DateTimeFormatter.ofPattern("d MMM")

/** Shows [prefix] before the typed text without making it part of what's typed. */
private class PrefixTransformation(private val prefix: String) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        // Nothing typed yet: the placeholder shows the ₹ instead.
        if (text.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        return TransformedText(
            AnnotatedString(prefix) + text,
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int) = offset + prefix.length
                override fun transformedToOriginal(offset: Int) = (offset - prefix.length).coerceAtLeast(0)
            },
        )
    }
}
