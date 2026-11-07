package com.mehul.inkless.ui.friends

import android.content.ComponentName
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mehul.inkless.R
import com.mehul.inkless.data.db.dao.ShareLine
import com.mehul.inkless.data.db.entity.IouEntity
import com.mehul.inkless.share.ShareActivity
import com.mehul.inkless.splits.FriendBalance
import com.mehul.inkless.splits.Settled
import com.mehul.inkless.ui.components.ChipTone
import com.mehul.inkless.ui.components.ConfirmDialog
import com.mehul.inkless.ui.components.InitialTile
import com.mehul.inkless.ui.components.InklessCard
import com.mehul.inkless.ui.components.InklessTopBar
import com.mehul.inkless.ui.components.LedgerRow
import com.mehul.inkless.ui.components.PrimaryButton
import com.mehul.inkless.ui.components.SecondaryButton
import com.mehul.inkless.ui.components.SectionHeader
import com.mehul.inkless.ui.components.TextLink
import com.mehul.inkless.ui.format.Money
import com.mehul.inkless.ui.theme.Inkless
import com.mehul.inkless.ui.theme.Spacing
import kotlinx.coroutines.launch

/** One friend: what's open between you, a reminder to send, money lent or borrowed, and what's settled. */
@Composable
fun FriendScreen(
    onBack: () -> Unit,
    onOpenTransaction: (String) -> Unit,
    viewModel: FriendViewModel = hiltViewModel(),
) {
    val balance by viewModel.balance.collectAsStateWithLifecycle()
    val settled by viewModel.settled.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    FriendContent(
        balance = balance,
        settled = settled,
        form = form,
        message = message,
        onBack = onBack,
        onOpenTransaction = onOpenTransaction,
        onRemind = {
            scope.launch {
                val text = viewModel.reminder() ?: return@launch
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                context.startActivity(
                    Intent.createChooser(send, "Remind ${balance?.friend?.name.orEmpty()}")
                        // Inkless can take shared text itself; it shouldn't offer itself here.
                        .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(context, ShareActivity::class.java))),
                )
            }
        },
        onSettleAll = viewModel::settleAll,
        onSettleShare = viewModel::settleShare,
        onMarkIncomingPaid = viewModel::markIncomingPaid,
        onAddIou = viewModel::addIou,
        onEditIou = viewModel::editIou,
        onSettleIou = viewModel::settleIou,
        onDeleteIou = viewModel::deleteIou,
        onEdit = viewModel::edit,
        onFormClosed = viewModel::formClosed,
        onRemove = { viewModel.remove(then = onBack) },
        onMessageShown = viewModel::messageShown,
    )
}

@Composable
fun FriendContent(
    balance: FriendBalance?,
    settled: List<Settled>,
    form: FriendForm,
    message: String?,
    onBack: () -> Unit,
    onOpenTransaction: (String) -> Unit,
    onRemind: () -> Unit,
    onSettleAll: () -> Unit,
    onSettleShare: (ShareLine) -> Unit,
    onMarkIncomingPaid: (String) -> Unit,
    onAddIou: (amountPaise: Long, theyOwe: Boolean, note: String?) -> Unit,
    onEditIou: (IouEntity, amountPaise: Long, theyOwe: Boolean, note: String?) -> Unit,
    onSettleIou: (IouEntity) -> Unit,
    onDeleteIou: (IouEntity) -> Unit,
    onEdit: (name: String, vpas: List<String>, username: String?) -> Unit,
    onFormClosed: () -> Unit,
    onRemove: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val colors = Inkless.colors
    var editing by rememberSaveable { mutableStateOf(false) }
    var removing by rememberSaveable { mutableStateOf(false) }
    var settlingAll by rememberSaveable { mutableStateOf(false) }
    var addingIou by rememberSaveable { mutableStateOf(false) }
    var editingIouId by rememberSaveable { mutableStateOf<String?>(null) }
    var showSettled by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); onMessageShown() }
    }
    LaunchedEffect(form.savedId) {
        if (form.savedId != null) {
            editing = false
            onFormClosed()
        }
    }

    Surface(Modifier.fillMaxSize(), color = colors.background) {
        Box {
            Column(Modifier.fillMaxSize()) {
                InklessTopBar(balance?.friend?.name.orEmpty(), onBack = onBack) {
                    if (balance != null) {
                        IconButton(onClick = { editing = true }) {
                            Icon(painterResource(R.drawable.icon_pencil), contentDescription = "Edit ${balance.friend.name}", tint = colors.ink)
                        }
                    }
                }
                val current = balance ?: return@Column
                val words = balanceWords(current.netPaise)
                val anythingOpen = current.openShares.isNotEmpty() || current.openIous.isNotEmpty()
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(horizontal = Spacing.gutter, vertical = Spacing.s),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xl),
                ) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        InitialTile(current.friend.name, size = 64.dp)
                        current.friend.linkedUsername?.let {
                            Text("@$it on Inkless", style = MaterialTheme.typography.bodySmall, color = colors.muted)
                        }
                        Text(
                            if (current.netPaise == 0L) "All square" else Money.format(kotlin.math.abs(current.netPaise)),
                            style = Inkless.type.moneyLarge,
                            color = if (words.tone == ChipTone.Good) colors.primary else colors.ink,
                        )
                        Text(
                            friendSentence(current.friend.name, current.netPaise),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.muted,
                            textAlign = TextAlign.Center,
                        )
                    }
                    if (current.netPaise > 0 || anythingOpen) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                            if (current.netPaise > 0) {
                                PrimaryButton("Remind", onClick = onRemind, modifier = Modifier.weight(1f), icon = R.drawable.icon_share_2)
                            }
                            if (anythingOpen) {
                                SecondaryButton("Settle all", onClick = { settlingAll = true }, modifier = Modifier.weight(1f), icon = R.drawable.icon_check_check)
                            }
                        }
                    }

                    if (current.openShares.isNotEmpty()) {
                        Section("Their part of your payments") {
                            current.openShares.forEachIndexed { i, line ->
                                if (i > 0) HorizontalDivider(color = colors.hairline)
                                LedgerRow(
                                    title = line.title ?: "Payment",
                                    amount = Money.format(line.sharePaise),
                                    meta = "${day(line.timestamp)} · owes you",
                                    onClick = { onOpenTransaction(line.transactionId) },
                                )
                                TextLink("Paid back", onClick = { onSettleShare(line) })
                            }
                        }
                    }
                    if (current.theirSplits.isNotEmpty()) {
                        Section("Your part of their payments") {
                            current.theirSplits.forEachIndexed { i, split ->
                                if (i > 0) HorizontalDivider(color = colors.hairline)
                                LedgerRow(
                                    title = split.note ?: "Split of ${Money.format(split.totalPaise)}",
                                    amount = Money.format(split.mySharePaise),
                                    meta = "${day(split.createdAt)} · you owe",
                                )
                                TextLink("I've paid", onClick = { onMarkIncomingPaid(split.id) })
                            }
                        }
                    }
                    Section("Lent and borrowed") {
                        if (current.openIous.isEmpty()) {
                            Text(
                                "Cash lent or borrowed outside a payment, like ₹150 for an auto.",
                                Modifier.padding(vertical = Spacing.m),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.muted,
                            )
                        }
                        current.openIous.forEachIndexed { i, iou ->
                            if (i > 0) HorizontalDivider(color = colors.hairline)
                            LedgerRow(
                                title = iou.note ?: if (iou.theyOwe) "You lent" else "You borrowed",
                                amount = Money.format(iou.amountPaise),
                                meta = "${day(iou.timestamp)} · ${if (iou.theyOwe) "they owe you" else "you owe them"}",
                                amountColor = if (iou.theyOwe) colors.primary else colors.ink,
                                onClick = { editingIouId = iou.id },
                            )
                        }
                        TextLink("+ Add lent or borrowed", onClick = { addingIou = true })
                    }

                    if (settled.isNotEmpty()) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SectionHeader("Settled · ${settled.size}", Modifier.weight(1f))
                                TextLink(if (showSettled) "Hide" else "Show", onClick = { showSettled = !showSettled })
                            }
                            if (showSettled) {
                                InklessCard(padding = PaddingValues(horizontal = Spacing.l, vertical = Spacing.xs)) {
                                    settled.forEachIndexed { i, item ->
                                        if (i > 0) HorizontalDivider(color = colors.hairline)
                                        when (item) {
                                            is Settled.Share -> LedgerRow(
                                                title = item.line.title ?: "Payment",
                                                amount = Money.format(item.line.sharePaise),
                                                meta = "${day(item.line.timestamp)} · paid back",
                                                amountColor = colors.muted,
                                                onClick = { onOpenTransaction(item.line.transactionId) },
                                            )
                                            is Settled.Iou -> LedgerRow(
                                                title = item.iou.note ?: if (item.iou.theyOwe) "You lent" else "You borrowed",
                                                amount = Money.format(item.iou.amountPaise),
                                                meta = "${day(item.iou.timestamp)} · paid back",
                                                amountColor = colors.muted,
                                                onClick = { editingIouId = item.iou.id },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TextLink("Remove ${current.friend.name}", onClick = { removing = true }, color = colors.danger)
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }

    val current = balance ?: return
    if (editing) {
        FriendSheet(current.friend, busy = form.busy, error = form.error, onSave = onEdit, onDismiss = { editing = false; onFormClosed() })
    }
    if (addingIou) {
        IouSheet(
            friendName = current.friend.name,
            iou = null,
            onSave = { amount, theyOwe, note -> onAddIou(amount, theyOwe, note); addingIou = false },
            onSettle = {},
            onDelete = {},
            onDismiss = { addingIou = false },
        )
    }
    val editingIou = editingIouId?.let { id ->
        current.openIous.firstOrNull { it.id == id } ?: settled.filterIsInstance<Settled.Iou>().firstOrNull { it.iou.id == id }?.iou
    }
    if (editingIou != null) {
        IouSheet(
            friendName = current.friend.name,
            iou = editingIou,
            onSave = { amount, theyOwe, note -> onEditIou(editingIou, amount, theyOwe, note); editingIouId = null },
            onSettle = { onSettleIou(editingIou); editingIouId = null },
            onDelete = { onDeleteIou(editingIou); editingIouId = null },
            onDismiss = { editingIouId = null },
        )
    }
    if (settlingAll) {
        ConfirmDialog(
            title = "Settle everything with ${current.friend.name}?",
            body = "For when you've squared up in cash, or with a payment Inkless didn't see. Their part of your payments " +
                "and everything lent or borrowed gets ticked off.",
            confirmText = "Settle all",
            onConfirm = { settlingAll = false; onSettleAll() },
            onDismiss = { settlingAll = false },
        )
    }
    if (removing) {
        ConfirmDialog(
            title = "Remove ${current.friend.name}?",
            body = "Splits you made with them stay as they are. They just won't show up here any more.",
            confirmText = "Remove",
            onConfirm = { removing = false; onRemove() },
            onDismiss = { removing = false },
            destructive = true,
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        SectionHeader(title)
        InklessCard(padding = PaddingValues(horizontal = Spacing.l, vertical = Spacing.xs)) { content() }
    }
}

/** "Rohan owes you, all told.", "You owe Priya.", "Nothing open between you." */
internal fun friendSentence(name: String, netPaise: Long): String {
    val first = name.substringBefore(' ')
    return when {
        netPaise > 0 -> "$first owes you, all told."
        netPaise < 0 -> "You owe $first, all told."
        else -> "Nothing open between you."
    }
}

