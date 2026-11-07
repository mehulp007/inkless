package com.mehul.inkless.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mehul.inkless.R
import com.mehul.inkless.data.db.entity.TransactionEntity
import com.mehul.inkless.ui.components.EmptyState
import com.mehul.inkless.ui.components.Footnote
import com.mehul.inkless.ui.components.InklessCard
import com.mehul.inkless.ui.components.InklessTopBar
import com.mehul.inkless.ui.components.Overline
import com.mehul.inkless.ui.components.PageHeadline
import com.mehul.inkless.ui.components.PrimaryButton
import com.mehul.inkless.ui.components.SectionHeader
import com.mehul.inkless.ui.components.StatusChip
import com.mehul.inkless.ui.components.TextLink
import com.mehul.inkless.ui.components.TintCard
import com.mehul.inkless.ui.format.Money
import com.mehul.inkless.ui.theme.Inkless
import com.mehul.inkless.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun ShareScreen(viewModel: ShareViewModel, sharedFrom: String?, onClose: () -> Unit, onContinue: () -> Unit) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val names by viewModel.categoryNames.collectAsStateWithLifecycle()
    ShareContent(ui, names, sharedFrom, onSelect = viewModel::select, onClose = onClose, onContinue = onContinue)
}

/**
 * What a shared order became: the items read from it, the payment they belong to, and a way on to
 * the break-down page to check and save them. Nothing is saved here.
 */
@Composable
fun ShareContent(
    ui: ShareUi,
    categoryNames: Map<String, String>,
    sharedFrom: String?,
    onSelect: (String) -> Unit,
    onClose: () -> Unit,
    onContinue: () -> Unit,
) {
    val colors = Inkless.colors
    Surface(Modifier.fillMaxSize(), color = colors.background) {
        Column(Modifier.fillMaxSize()) {
            InklessTopBar("Review shared items", onBack = onClose, marker = "Draft")
            when (ui) {
                ShareUi.Reading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
                        CircularProgressIndicator(color = colors.primary)
                        Text("Reading your order on this phone…", style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                    }
                }
                is ShareUi.Problem -> Box(Modifier.fillMaxSize().padding(Spacing.gutter), Alignment.Center) {
                    EmptyState(
                        title = "Nothing to break down",
                        body = ui.message,
                        icon = R.drawable.icon_receipt,
                        action = "Close",
                        onAction = onClose,
                    )
                }
                is ShareUi.Ready -> Ready(ui, categoryNames, sharedFrom, onSelect, onClose, onContinue)
            }
        }
    }
}

@Composable
private fun Ready(
    ui: ShareUi.Ready,
    categoryNames: Map<String, String>,
    sharedFrom: String?,
    onSelect: (String) -> Unit,
    onClose: () -> Unit,
    onContinue: () -> Unit,
) {
    val colors = Inkless.colors
    var choosing by rememberSaveable { mutableStateOf(ui.matchedId == null) }
    val selected = ui.payments.firstOrNull { it.id == ui.selectedId }
    val itemsTotal = ui.items.sumOf { it.amountPaise }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = Spacing.gutter, vertical = Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
            StatusChip(sharedFrom?.let { "Shared from $it" } ?: "Shared order")
            PageHeadline(
                "A receipt becomes a clearer ledger.",
                lead = "We read what you shared on your phone. Check each item before you confirm.",
            )
        }
        TintCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Icon(painterResource(R.drawable.icon_shield_check), contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
                Text("Read locally. Not uploaded or kept.", style = MaterialTheme.typography.titleSmall, color = colors.ink)
            }
            Text(
                "Nothing is saved until you confirm. Only the item details you approve join your ledger.",
                Modifier.padding(top = Spacing.s),
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted,
            )
        }

        InklessCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Icon(painterResource(R.drawable.icon_link), contentDescription = null, tint = colors.primary, modifier = Modifier.size(16.dp))
                Overline(if (selected != null && selected.id == ui.matchedId) "Matched existing payment" else "Payment it belongs to")
            }
            if (selected != null) {
                Row(Modifier.padding(top = Spacing.m), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${title(selected)} · ${day(selected)}", style = MaterialTheme.typography.titleMedium, color = colors.ink)
                        Text(time(selected), style = MaterialTheme.typography.bodySmall, color = colors.muted)
                    }
                    Text(Money.format(selected.amountPaise), style = Inkless.type.moneyRow.copy(fontSize = 22.sp), color = colors.ink)
                }
            } else {
                Text("Pick the payment these items were bought with.", Modifier.padding(top = Spacing.m), style = MaterialTheme.typography.bodyMedium, color = colors.muted)
            }
            TextLink(if (choosing) "Hide the other payments" else "Not the right payment? Choose another →", onClick = { choosing = !choosing })
            if (choosing) {
                ui.payments.forEach { payment ->
                    PaymentChoice(payment, sameAmount = payment.id == ui.matchedId, selected = payment.id == ui.selectedId, onSelect = { onSelect(payment.id) })
                }
            }
        }

        Column {
            SectionHeader("${ui.items.size} ${if (ui.items.size == 1) "item" else "items"} found")
            ui.items.forEach { item ->
                Row(Modifier.fillMaxWidth().padding(vertical = Spacing.m), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.note ?: "Item", style = MaterialTheme.typography.bodyLarge, color = colors.ink)
                        Text(categoryNames[item.categoryId] ?: "Category to pick", style = MaterialTheme.typography.bodySmall, color = colors.muted)
                    }
                    Text(Money.format(item.amountPaise), style = Inkless.type.moneyRow, color = colors.ink)
                }
            }
            HorizontalDivider(color = colors.hairline)
            Row(Modifier.fillMaxWidth().padding(top = Spacing.m), verticalAlignment = Alignment.CenterVertically) {
                Text(totalLine(itemsTotal, selected?.amountPaise), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = colors.ink)
                Text(Money.format(itemsTotal, alwaysShowPaise = true), style = Inkless.type.moneyRow.copy(fontSize = 22.sp), color = colors.ink)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            PrimaryButton("Confirm items", onClick = onContinue, enabled = selected != null)
            Footnote("Next you see them on the break-down page, ready to save. They become this payment's items, not a new expense.")
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TextLink("Discard draft", onClick = onClose) }
        }
    }
}

@Composable
private fun PaymentChoice(payment: TransactionEntity, sameAmount: Boolean, selected: Boolean, onSelect: () -> Unit) {
    val colors = Inkless.colors
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onSelect).padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = colors.primary))
        Column(Modifier.weight(1f).padding(start = Spacing.s)) {
            Text(title(payment), style = MaterialTheme.typography.bodyLarge, color = colors.ink)
            Text(
                "${day(payment)} · ${time(payment)}" + if (sameAmount) " · same amount as the order" else "",
                style = MaterialTheme.typography.bodySmall,
                color = if (sameAmount) colors.primary else colors.muted,
            )
        }
        Text(Money.format(payment.amountPaise), style = Inkless.type.moneyRow, color = colors.ink, textAlign = TextAlign.End)
    }
}

/** "Total · matches payment", or how far off the order is (the last item takes the difference). */
internal fun totalLine(itemsPaise: Long, paymentPaise: Long?): String = when {
    paymentPaise == null -> "Total"
    itemsPaise == paymentPaise -> "Total · matches payment"
    itemsPaise < paymentPaise -> "Total · ${Money.format(paymentPaise - itemsPaise)} less than the payment"
    else -> "Total · ${Money.format(itemsPaise - paymentPaise)} more than the payment"
}

private fun title(payment: TransactionEntity) = payment.payeeRaw ?: payment.vpa ?: "Payment"

private fun day(payment: TransactionEntity) = Instant.ofEpochMilli(payment.timestamp).atZone(ZoneId.systemDefault()).format(DAY)

private fun time(payment: TransactionEntity) = Instant.ofEpochMilli(payment.timestamp).atZone(ZoneId.systemDefault()).format(TIME)

private val DAY = DateTimeFormatter.ofPattern("d MMM")
private val TIME = DateTimeFormatter.ofPattern("HH:mm")
