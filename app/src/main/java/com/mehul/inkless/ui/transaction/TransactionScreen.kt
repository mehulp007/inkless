package com.mehul.inkless.ui.transaction

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import com.mehul.inkless.ui.format.Money
import com.mehul.inkless.ui.format.Titles
import com.mehul.inkless.ui.theme.LocalInklessColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionScreen(
    onBack: () -> Unit,
    onBreakDown: (transactionId: String) -> Unit,
    onSplit: (transactionId: String) -> Unit,
    viewModel: TransactionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.transaction?.let(::screenTitle) ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.transaction != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(painterResource(R.drawable.ic_delete), contentDescription = "Delete")
                        }
                    }
                },
            )
        },
    ) { padding ->
        val txn = state.transaction
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            txn == null -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Text("This entry was deleted.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> Details(
                txn = txn,
                state = state,
                modifier = Modifier.padding(padding).consumeWindowInsets(padding),
                onSave = { category, note -> viewModel.save(category, note, then = onBack) },
                onRename = viewModel::renameMerchant,
                onBreakDown = { onBreakDown(txn.id) },
                onRemoveBreakdown = viewModel::removeBreakdown,
                onSplit = { onSplit(txn.id) },
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this entry?") },
            text = { Text("It disappears from your totals. Use this for anything that wasn't really a payment.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete(then = onBack) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Details(
    txn: TransactionEntity,
    state: TransactionScreenState,
    modifier: Modifier,
    onSave: (categoryId: String?, note: String) -> Unit,
    onRename: (String) -> Unit,
    onBreakDown: () -> Unit,
    onRemoveBreakdown: () -> Unit,
    onSplit: () -> Unit,
) {
    var renaming by rememberSaveable { mutableStateOf(false) }
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = (if (isCredit) "+" else "−") + Money.format(txn.amountPaise, alwaysShowPaise = true),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
                color = if (isCredit) LocalInklessColors.current.credit else MaterialTheme.colorScheme.onSurface,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    state.merchant?.displayName ?: txn.payeeRaw ?: txn.vpa ?: Titles.fallback(txn.type),
                    style = MaterialTheme.typography.titleMedium,
                )
                // Renaming changes the name on every payment to this merchant.
                if (state.merchant != null) {
                    IconButton(onClick = { renaming = true }) {
                        Icon(painterResource(R.drawable.ic_edit), contentDescription = "Rename merchant")
                    }
                }
            }
            Text(
                Instant.ofEpochMilli(txn.timestamp).atZone(ZoneId.systemDefault()).format(WHEN_FORMAT),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (brokenDown) {
            BreakdownList(state, onEdit = onBreakDown, onRemove = onRemoveBreakdown)
        } else if (takesCategory) {
            if (txn.status == TransactionStatus.NEEDS_REVIEW) {
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.small) {
                    val guessed = state.guess?.let { g -> state.categories.firstOrNull { it.id == g.categoryId } }
                    Text(
                        guessed?.let { "Captured automatically. Looks like ${it.emoji} ${it.name} — tap Save if that's right, or pick another." }
                            ?: "Captured automatically — pick what this was.",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
            Text("Category", style = MaterialTheme.typography.titleSmall)
            CategoryPicker(categories = state.categories, selectedId = categoryId, onSelect = { categoryId = it })
            if (canBreakDown) {
                OutlinedButton(onClick = onBreakDown, modifier = Modifier.fillMaxWidth()) {
                    Text("Several things? Break it down")
                }
            }
        }

        if (split != null) {
            SplitSummary(txn, split, state.friendNames, onEdit = onSplit)
        } else if (canSplit) {
            OutlinedButton(onClick = onSplit, modifier = Modifier.fillMaxWidth()) {
                Text("Split with friends")
            }
        }

        OutlinedTextField(
            value = note,
            onValueChange = { note = it.take(200) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Note") },
        )

        Button(
            onClick = { onSave(categoryId, note) },
            enabled = changed,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Text("Save") }

        HorizontalDivider()
        InfoRows(txn)
    }

    if (renaming && state.merchant != null) {
        RenameDialog(
            current = state.merchant.displayName,
            onRename = { onRename(it); renaming = false },
            onDismiss = { renaming = false },
        )
    }
}

@Composable
private fun BreakdownList(state: TransactionScreenState, onEdit: () -> Unit, onRemove: () -> Unit) {
    val categories = state.categories.associateBy { it.id }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Broken down into ${state.items.size} items", style = MaterialTheme.typography.titleSmall)
        state.items.forEach { item ->
            val category = categories[item.categoryId]
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${category?.emoji.orEmpty()}  ${category?.name ?: "Other"}", style = MaterialTheme.typography.bodyLarge)
                    item.note?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(Money.format(item.amountPaise), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onEdit) { Text("Edit items") }
            TextButton(onClick = onRemove) { Text("Back to one category") }
        }
    }
}

@Composable
private fun SplitSummary(txn: TransactionEntity, split: SplitView, names: Map<String, String>, onEdit: () -> Unit) {
    val mine = txn.amountPaise - split.shares.sumOf { it.sharePaise }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Split with ${split.shares.size} ${if (split.shares.size == 1) "friend" else "friends"}", style = MaterialTheme.typography.titleSmall)
        split.shares.forEach { share ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(names[share.friendId] ?: "Removed friend", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (share.settled) "paid you back" else "owes you",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (share.settled) LocalInklessColors.current.credit else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(Money.format(share.sharePaise), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Your part", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(Money.format(mine), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
        OutlinedButton(onClick = onEdit) { Text("Edit split") }
    }
}

@Composable
private fun RenameDialog(current: String, onRename: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename merchant") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Changes the name on every payment to them, e.g. \"q8291…@ybl\" → \"Tapri near Block C\".",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onRename(name) }, enabled = name.isNotBlank() && name.trim() != current) { Text("Rename") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun InfoRows(txn: TransactionEntity) {
    val rows = listOfNotNull(
        txn.vpa?.let { "UPI ID" to it },
        txn.accountLast4?.let { "Account" to "••$it" },
        txn.bankCode?.let { "Bank" to it },
        txn.upiRef?.let { "UPI ref" to it },
        txn.balanceAfterPaise?.let { "Balance after" to Money.format(it, alwaysShowPaise = true) },
        "Added from" to txn.sources.joinToString { sourceLabel(it) },
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { (label, value) ->
            Row(Modifier.fillMaxWidth()) {
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(value, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun screenTitle(txn: TransactionEntity) = when (txn.type) {
    TransactionType.INCOME -> "Money in"
    TransactionType.REFUND -> "Refund"
    else -> if (CaptureSource.MANUAL in txn.sources) "Cash" else "Payment"
}

private fun sourceLabel(source: CaptureSource) = when (source) {
    CaptureSource.SMS -> "Bank SMS"
    CaptureSource.NOTIFICATION -> "App notification"
    CaptureSource.MANUAL -> "Added by hand"
    CaptureSource.SCAN_PAY -> "Scan & pay"
}

private val WHEN_FORMAT = DateTimeFormatter.ofPattern("EEE, d MMM yyyy · h:mm a")
