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
) {
    // Keyed by id so the fields reset if the screen is reused for another entry.
    var categoryId by rememberSaveable(txn.id) { mutableStateOf(txn.categoryId) }
    var note by rememberSaveable(txn.id) { mutableStateOf(txn.note.orEmpty()) }
    val changed = categoryId != txn.categoryId || note.trim() != txn.note.orEmpty() ||
        (txn.status == TransactionStatus.NEEDS_REVIEW && categoryId != null)
    val isCredit = txn.direction == Direction.CREDIT
    val takesCategory = txn.type == TransactionType.EXPENSE || txn.type == TransactionType.REFUND

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
            Text(txn.payeeRaw ?: txn.vpa ?: Titles.fallback(txn.type), style = MaterialTheme.typography.titleMedium)
            Text(
                Instant.ofEpochMilli(txn.timestamp).atZone(ZoneId.systemDefault()).format(WHEN_FORMAT),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (takesCategory) {
            if (txn.status == TransactionStatus.NEEDS_REVIEW) {
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.small) {
                    Text(
                        "Captured automatically — pick what this was.",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
            Text("Category", style = MaterialTheme.typography.titleSmall)
            CategoryPicker(categories = state.categories, selectedId = categoryId, onSelect = { categoryId = it })
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
