package com.mehul.inkless.ui.friends

import android.content.ComponentName
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mehul.inkless.R
import com.mehul.inkless.parsing.Amounts
import com.mehul.inkless.share.ShareActivity
import com.mehul.inkless.splits.FriendBalance
import com.mehul.inkless.ui.components.SectionHeader
import com.mehul.inkless.ui.format.Money
import com.mehul.inkless.ui.theme.LocalInklessColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/** One friend: what's still open between you, a reminder to send, and money lent or borrowed outside a split. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendScreen(
    onBack: () -> Unit,
    onOpenTransaction: (String) -> Unit,
    viewModel: FriendViewModel = hiltViewModel(),
) {
    val balance by viewModel.balance.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf(false) }
    var removing by rememberSaveable { mutableStateOf(false) }
    var settling by rememberSaveable { mutableStateOf(false) }
    var addingIou by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); viewModel.messageShown() }
    }
    LaunchedEffect(form.savedId) {
        if (form.savedId != null) {
            editing = false
            viewModel.formClosed()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(balance?.friend?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back") }
                },
                actions = {
                    if (balance != null) {
                        IconButton(onClick = { editing = true }) { Icon(painterResource(R.drawable.ic_edit), contentDescription = "Edit") }
                        IconButton(onClick = { removing = true }) { Icon(painterResource(R.drawable.ic_delete), contentDescription = "Remove") }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { addingIou = true },
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                text = { Text("Lent or borrowed") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val current = balance
        if (current == null) {
            Box(Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                Header(
                    balance = current,
                    onRemind = {
                        scope.launch {
                            val text = viewModel.reminder() ?: return@launch
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                            context.startActivity(
                                Intent.createChooser(send, "Remind ${current.friend.name}")
                                    .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(context, ShareActivity::class.java))),
                            )
                        }
                    },
                    onSettle = { settling = true },
                )
            }
            if (current.openShares.isNotEmpty()) {
                item { SectionHeader("Their part of your payments", Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp)) }
                items(current.openShares, key = { "s-${it.shareId}" }) { line ->
                    ListItem(
                        modifier = Modifier.clickable { onOpenTransaction(line.transactionId) },
                        headlineContent = { Text(line.title ?: "Payment") },
                        supportingContent = { Text(day(line.timestamp)) },
                        trailingContent = { Text(Money.format(line.sharePaise), fontWeight = FontWeight.Medium) },
                    )
                }
            }
            if (current.theirSplits.isNotEmpty()) {
                item { SectionHeader("Your part of their payments", Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp)) }
                items(current.theirSplits, key = { "in-${it.id}" }) { split ->
                    ListItem(
                        headlineContent = { Text(split.note ?: "Split of ${Money.format(split.totalPaise)}") },
                        supportingContent = { Text("${day(split.createdAt)} · ${Money.format(split.mySharePaise)}") },
                        trailingContent = { TextButton(onClick = { viewModel.markIncomingPaid(split.id) }) { Text("I've paid") } },
                    )
                }
            }
            if (current.openIous.isNotEmpty()) {
                item { SectionHeader("Lent and borrowed", Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp)) }
                items(current.openIous, key = { "i-${it.id}" }) { iou ->
                    ListItem(
                        headlineContent = { Text(iou.note ?: if (iou.theyOwe) "You lent" else "You borrowed") },
                        supportingContent = { Text(day(iou.timestamp) + if (iou.theyOwe) " · they owe you" else " · you owe them") },
                        trailingContent = {
                            Text(
                                Money.format(iou.amountPaise),
                                fontWeight = FontWeight.Medium,
                                color = if (iou.theyOwe) LocalInklessColors.current.credit else MaterialTheme.colorScheme.error,
                            )
                        },
                    )
                }
            }
        }
    }

    val current = balance
    if (editing && current != null) {
        FriendDialog(
            friend = current.friend,
            busy = form.busy,
            error = form.error,
            onSave = viewModel::edit,
            onDismiss = { editing = false; viewModel.formClosed() },
        )
    }
    if (removing && current != null) {
        AlertDialog(
            onDismissRequest = { removing = false },
            title = { Text("Remove ${current.friend.name}?") },
            text = { Text("Splits you made with them stay as they are. They just won't show up here any more.") },
            confirmButton = {
                TextButton(onClick = { removing = false; viewModel.remove(then = onBack) }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { removing = false }) { Text("Cancel") } },
        )
    }
    if (settling && current != null) {
        AlertDialog(
            onDismissRequest = { settling = false },
            title = { Text("Mark everything settled?") },
            text = {
                Text(
                    "For when you've squared up in cash, or with a payment Inkless didn't see. " +
                        "Their part of your payments and everything lent or borrowed gets ticked off.",
                )
            },
            confirmButton = { TextButton(onClick = { settling = false; viewModel.settleAll() }) { Text("Mark settled") } },
            dismissButton = { TextButton(onClick = { settling = false }) { Text("Cancel") } },
        )
    }
    if (addingIou && current != null) {
        IouDialog(
            friendName = current.friend.name,
            onSave = { amount, theyOwe, note -> viewModel.addIou(amount, theyOwe, note); addingIou = false },
            onDismiss = { addingIou = false },
        )
    }
}

@Composable
private fun Header(balance: FriendBalance, onRemind: () -> Unit, onSettle: () -> Unit) {
    val (text, color) = balanceText(balance.netPaise)
    val anythingOpen = balance.openShares.isNotEmpty() || balance.openIous.isNotEmpty()
    Column(
        Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
        balance.friend.linkedUsername?.let {
            Text("@$it on Inkless", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (balance.netPaise > 0) {
                Button(onClick = onRemind) {
                    Icon(painterResource(R.drawable.ic_share), contentDescription = null)
                    Text("  Remind")
                }
            }
            if (anythingOpen) OutlinedButton(onClick = onSettle) { Text("Mark all settled") }
        }
    }
}

/** Money lent to or borrowed from a friend outside any payment Inkless saw. */
@Composable
private fun IouDialog(friendName: String, onSave: (amountPaise: Long, theyOwe: Boolean, note: String?) -> Unit, onDismiss: () -> Unit) {
    var amount by rememberSaveable { mutableStateOf("") }
    var theyOwe by rememberSaveable { mutableStateOf(true) }
    var note by rememberSaveable { mutableStateOf("") }
    val paise = Amounts.toPaise(amount)?.takeIf { it > 0 }
    val first = friendName.substringBefore(' ')

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lent or borrowed") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = theyOwe, onClick = { theyOwe = true }, label = { Text("I lent $first") })
                    FilterChip(selected = !theyOwe, onClick = { theyOwe = false }, label = { Text("$first lent me") })
                }
                OutlinedTextField(
                    amount,
                    { amount = it.take(10) },
                    Modifier.fillMaxWidth(),
                    label = { Text("Amount") },
                    prefix = { Text("₹") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(note, { note = it.take(60) }, Modifier.fillMaxWidth(), label = { Text("What for? (optional)") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = { paise?.let { onSave(it, theyOwe, note) } }, enabled = paise != null) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}


private val DAY = DateTimeFormatter.ofPattern("d MMM")

private fun day(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DAY)
