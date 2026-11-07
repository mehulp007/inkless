package com.mehul.inkless.ui.transaction

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mehul.inkless.R
import com.mehul.inkless.data.db.entity.CaptureSource
import com.mehul.inkless.data.db.entity.Direction
import com.mehul.inkless.data.db.entity.TransactionEntity
import com.mehul.inkless.data.db.entity.TransactionStatus
import com.mehul.inkless.data.db.entity.TransactionType
import com.mehul.inkless.splits.SplitView
import com.mehul.inkless.ui.components.CategoryPicker
import com.mehul.inkless.ui.components.CategoryTile
import com.mehul.inkless.ui.components.ChipTone
import com.mehul.inkless.ui.components.ChoiceField
import com.mehul.inkless.ui.components.ConfirmDialog
import com.mehul.inkless.ui.components.InfoRow
import com.mehul.inkless.ui.components.InklessBottomSheet
import com.mehul.inkless.ui.components.InklessCard
import com.mehul.inkless.ui.components.InklessTextField
import com.mehul.inkless.ui.components.InklessTopBar
import com.mehul.inkless.ui.components.PrimaryButton
import com.mehul.inkless.ui.components.StatusChip
import com.mehul.inkless.ui.components.TextLink
import com.mehul.inkless.ui.components.TintCard
import com.mehul.inkless.ui.components.WarmCard
import com.mehul.inkless.ui.format.Money
import com.mehul.inkless.ui.format.Titles
import com.mehul.inkless.ui.theme.Inkless
import com.mehul.inkless.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TransactionScreen(
    onBack: () -> Unit,
    onBreakDown: (transactionId: String) -> Unit,
    onSplit: (transactionId: String) -> Unit,
    viewModel: TransactionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TransactionContent(
        state = state,
        onBack = onBack,
        onSave = { category, note -> viewModel.save(category, note, then = onBack) },
        onRename = viewModel::renameMerchant,
        onBreakDown = { state.transaction?.let { onBreakDown(it.id) } },
        onRemoveBreakdown = viewModel::removeBreakdown,
        onSplit = { state.transaction?.let { onSplit(it.id) } },
        onDelete = { viewModel.delete(then = onBack) },
    )
}

/** The payment page without its view model. */
@Composable
fun TransactionContent(
    state: TransactionScreenState,
    onBack: () -> Unit,
    onSave: (categoryId: String?, note: String) -> Unit,
    onRename: (String) -> Unit,
    onBreakDown: () -> Unit,
    onRemoveBreakdown: () -> Unit,
    onSplit: () -> Unit,
    onDelete: () -> Unit,
) {
    val txn = state.transaction
    Surface(Modifier.fillMaxSize(), color = Inkless.colors.background) {
        Column(Modifier.fillMaxSize()) {
            InklessTopBar(txn?.let(::screenTitle) ?: "Payment details", onBack = onBack)
            when {
                state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Inkless.colors.primary) }
                txn == null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("This entry was deleted.", color = Inkless.colors.muted)
                }
                else -> Details(txn, state, onSave, onRename, onBreakDown, onRemoveBreakdown, onSplit, onDelete)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Details(
    txn: TransactionEntity,
    state: TransactionScreenState,
    onSave: (categoryId: String?, note: String) -> Unit,
    onRename: (String) -> Unit,
    onBreakDown: () -> Unit,
    onRemoveBreakdown: () -> Unit,
    onSplit: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = Inkless.colors
    var renaming by rememberSaveable { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    // Keyed by id so the fields reset if the screen is reused for another entry.
    // A payment waiting for a category starts on the model's guess, so a right guess is one tap.
    var categoryId by rememberSaveable(txn.id) {
        mutableStateOf(state.guess?.categoryId?.takeIf { txn.status == TransactionStatus.NEEDS_REVIEW } ?: txn.categoryId)
    }
    var note by rememberSaveable(txn.id) { mutableStateOf(txn.note.orEmpty()) }
    val changed = categoryId != txn.categoryId || note.trim() != txn.note.orEmpty() ||
        (txn.status == TransactionStatus.NEEDS_REVIEW && categoryId != null)
    val isCredit = txn.direction == Direction.CREDIT
    val takesCategory = txn.type == TransactionType.EXPENSE || txn.type == TransactionType.REFUND
    // Only spending can be split into what it was for, or with friends; refunds and money in stay whole.
    // A payment is one or the other: broken down into items, or split with friends.
    val brokenDown = state.items.isNotEmpty()
    val split = state.split
    val canBreakDown = txn.type == TransactionType.EXPENSE && !isCredit && split == null
    val canSplit = txn.type == TransactionType.EXPENSE && !isCredit && !brokenDown
    val byId = state.categories.associateBy { it.id }
    val name = state.merchant?.displayName ?: txn.payeeRaw ?: txn.vpa ?: Titles.fallback(txn.type)

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = Spacing.gutter, vertical = Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            CategoryTile(
                if (brokenDown) state.items.maxByOrNull { it.amountPaise }?.categoryId else categoryId,
                isCredit = isCredit,
                isTransfer = txn.type == TransactionType.SELF_TRANSFER,
                size = 56.dp,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.titleLarge, color = colors.ink, textAlign = TextAlign.Center)
                // Renaming changes the name on every payment to this merchant.
                if (state.merchant != null) {
                    IconButton(onClick = { renaming = true }, modifier = Modifier.size(36.dp)) {
                        Icon(painterResource(R.drawable.icon_pencil), contentDescription = "Rename merchant", tint = colors.primary, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Text(
                (if (isCredit) "+" else "") + Money.format(txn.amountPaise),
                style = Inkless.type.moneyLarge,
                color = if (isCredit && txn.type != TransactionType.SELF_TRANSFER) colors.credit else colors.ink,
            )
            StatusChip(typeLine(txn, state), tone = if (txn.status == TransactionStatus.NEEDS_REVIEW) ChipTone.Check else ChipTone.Good)
        }

        InklessCard {
            facts(txn).forEach { (label, value) -> InfoRow(label, value) }
            if (CaptureSource.SMS in txn.sources && CaptureSource.NOTIFICATION in txn.sources) {
                Text(
                    "Two alerts, one payment. The bank's details are kept together.",
                    Modifier.padding(top = Spacing.s),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.primary,
                )
            }
        }

        if (txn.status == TransactionStatus.NEEDS_REVIEW && takesCategory && !brokenDown) {
            WarmCard {
                val guessed = state.guess?.let { byId[it.categoryId] }
                Text(
                    guessed?.let { "Looks like ${it.name}. Save if that's right, or pick another." }
                        ?: "Captured automatically. Pick what this was.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        if (brokenDown) {
            val biggest = state.items.maxByOrNull { it.amountPaise }
            val others = state.items.filter { it.categoryId != biggest?.categoryId }.groupBy { it.categoryId }
            ChoiceField(
                value = byId[biggest?.categoryId]?.name ?: "Other",
                onClick = onBreakDown,
                label = "Main category · biggest item",
                note = listOfNotNull(
                    "${Money.format(state.items.filter { it.categoryId == biggest?.categoryId }.sumOf { it.amountPaise })} here",
                ).plus(others.map { (id, items) -> "${Money.format(items.sumOf { it.amountPaise })} in ${byId[id]?.name ?: "Other"}" })
                    .joinToString(" · "),
                icon = R.drawable.icon_list_tree,
            )
        } else if (takesCategory) {
            ChoiceField(
                value = categoryId?.let { byId[it]?.name } ?: "Choose a category",
                onClick = { picking = true },
                label = "Category",
            )
        }

        InklessTextField(
            value = note,
            onValueChange = { note = it.take(200) },
            label = "Note",
            placeholder = "Add a note",
            trailingIcon = R.drawable.icon_pencil,
            singleLine = false,
        )

        when {
            brokenDown -> TintCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${state.items.size} items · exact total ${Money.format(txn.amountPaise)}",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.ink,
                    )
                    Icon(painterResource(R.drawable.icon_list_tree), contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
                }
                Text(
                    itemsSentence(state.items.mapNotNull { it.note?.takeIf(String::isNotBlank) }),
                    Modifier.padding(top = Spacing.xs),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                )
                TextLink("Break down · view or edit items →", onClick = onBreakDown)
                TextLink("Back to one category", onClick = onRemoveBreakdown, color = colors.muted)
            }
            canBreakDown -> TintCard {
                Text("Several things in one payment?", style = MaterialTheme.typography.titleSmall, color = colors.ink)
                Text(
                    "Break it into items, each in its own category.",
                    Modifier.padding(top = Spacing.xs),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                )
                TextLink("Break it down →", onClick = onBreakDown)
            }
        }

        if (split != null) {
            SplitCard(txn, split, state.friendNames, onEdit = onSplit)
        } else if (canSplit) {
            TintCard {
                Text("Shared this with friends?", style = MaterialTheme.typography.titleSmall, color = colors.ink)
                Text(
                    "Split it, and only your part counts as your spending.",
                    Modifier.padding(top = Spacing.xs),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                )
                TextLink("Split with friends →", onClick = onSplit)
            }
        }

        PrimaryButton("Save changes", onClick = { onSave(categoryId, note) }, enabled = changed)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TextLink("Delete this entry", onClick = { confirmDelete = true }, color = colors.danger)
        }
    }

    if (picking) {
        InklessBottomSheet(onDismiss = { picking = false }) {
            Text("What was this for?", style = MaterialTheme.typography.titleLarge, color = colors.ink)
            CategoryPicker(categories = state.categories, selectedId = categoryId, onSelect = { categoryId = it; picking = false })
        }
    }
    if (renaming && state.merchant != null) {
        RenameSheet(current = state.merchant.displayName, onRename = { onRename(it); renaming = false }, onDismiss = { renaming = false })
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete this entry?",
            body = "It disappears from your totals. Use this for anything that wasn't really a payment.",
            confirmText = "Delete",
            onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false },
            destructive = true,
        )
    }
}

@Composable
private fun SplitCard(txn: TransactionEntity, split: SplitView, names: Map<String, String>, onEdit: () -> Unit) {
    val colors = Inkless.colors
    val mine = txn.amountPaise - split.shares.sumOf { it.sharePaise }
    InklessCard {
        Text(
            "Split with ${split.shares.size} ${if (split.shares.size == 1) "friend" else "friends"}",
            style = MaterialTheme.typography.titleSmall,
            color = colors.ink,
        )
        split.shares.forEach { share ->
            InfoRow(
                names[share.friendId] ?: "Removed friend",
                Money.format(share.sharePaise) + if (share.settled) " · paid back" else " · owes you",
            )
        }
        HorizontalDivider(Modifier.padding(vertical = Spacing.xs), color = colors.hairline)
        InfoRow("Your part", Money.format(mine))
        TextLink("Edit split →", onClick = onEdit)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RenameSheet(current: String, onRename: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(current) }
    InklessBottomSheet(onDismiss = onDismiss) {
        Text("Rename merchant", style = MaterialTheme.typography.titleLarge, color = Inkless.colors.ink)
        InklessTextField(
            value = name,
            onValueChange = { name = it.take(40) },
            label = "Name",
            note = "Changes the name on every payment to them, e.g. \"q8291…@ybl\" → \"Tapri near Block C\".",
        )
        PrimaryButton("Rename", onClick = { onRename(name.trim()) }, enabled = name.isNotBlank() && name.trim() != current)
    }
}

/** "Expense · 1 payment, 4 items", "Expense · to sort", "Refund", "Own-account transfer" */
internal fun typeLine(txn: TransactionEntity, state: TransactionScreenState): String = when {
    txn.type == TransactionType.SELF_TRANSFER -> "Own-account transfer · not spending"
    txn.type == TransactionType.REFUND -> "Refund · counts against its category"
    txn.direction == Direction.CREDIT -> "Money in · not spending"
    state.items.isNotEmpty() -> "Expense · 1 payment, ${state.items.size} items"
    state.split != null -> "Expense · split with ${state.split.shares.size}"
    txn.status == TransactionStatus.NEEDS_REVIEW -> "Expense · to sort"
    CaptureSource.MANUAL in txn.sources -> "Cash expense"
    else -> "Expense"
}

/** The bank's facts, each only when it's known. Only the last four digits of a reference show. */
internal fun facts(txn: TransactionEntity, zone: ZoneId = ZoneId.systemDefault()): List<Pair<String, String>> = listOfNotNull(
    "Date" to Instant.ofEpochMilli(txn.timestamp).atZone(zone).format(WHEN),
    "Source" to sourceLine(txn.sources),
    (txn.bankCode ?: txn.accountLast4)?.let {
        "Paid from" to listOfNotNull(txn.bankCode, txn.accountLast4?.let { "•• $it" }).joinToString(" ")
    },
    txn.vpa?.let { "UPI ID" to it },
    txn.upiRef?.takeIf { it.length >= 4 }?.let { "UPI reference" to "•••• ${it.takeLast(4)}" },
    txn.balanceAfterPaise?.let { "Balance after" to Money.format(it, alwaysShowPaise = true) },
)

private fun sourceLine(sources: Set<CaptureSource>): String = sources.sortedBy { it.ordinal }.joinToString(" + ") {
    when (it) {
        CaptureSource.SMS -> "Bank SMS"
        CaptureSource.NOTIFICATION -> "UPI app"
        CaptureSource.MANUAL -> "Added by hand"
        CaptureSource.SCAN_PAY -> "Scan & pay"
    }
}.ifEmpty { "Added by hand" }

/** "Milk, bananas, snacks and a notebook." */
internal fun itemsSentence(names: List<String>): String {
    val tail = "Category totals use each item's amount."
    if (names.isEmpty()) return tail
    val lowered = names.mapIndexed { i, n -> if (i == 0) n else n.replaceFirstChar { c -> c.lowercase() } }
    val list = if (lowered.size == 1) lowered.single() else lowered.dropLast(1).joinToString(", ") + " and " + lowered.last()
    return "$list. $tail"
}

private fun screenTitle(txn: TransactionEntity) = when (txn.type) {
    TransactionType.INCOME -> "Money in"
    TransactionType.REFUND -> "Refund"
    TransactionType.SELF_TRANSFER -> "Transfer"
    else -> if (CaptureSource.MANUAL in txn.sources) "Cash payment" else "Payment details"
}

private val WHEN = DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm")

