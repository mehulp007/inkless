package com.mehul.inkless.ui.friends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mehul.inkless.R
import com.mehul.inkless.data.db.entity.IncomingSplitEntity
import com.mehul.inkless.splits.FriendBalance
import com.mehul.inkless.splits.FriendTotals
import com.mehul.inkless.ui.components.ChipTone
import com.mehul.inkless.ui.components.EmptyState
import com.mehul.inkless.ui.components.InitialTile
import com.mehul.inkless.ui.components.InklessCard
import com.mehul.inkless.ui.components.InklessTopBar
import com.mehul.inkless.ui.components.LedgerRow
import com.mehul.inkless.ui.components.PageHeadline
import com.mehul.inkless.ui.components.PrimaryButton
import com.mehul.inkless.ui.components.SectionHeader
import com.mehul.inkless.ui.components.TextLink
import com.mehul.inkless.ui.components.TintCard
import com.mehul.inkless.ui.format.Money
import com.mehul.inkless.ui.theme.Inkless
import com.mehul.inkless.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Who owes whom: every friend's balance, and splits friends shared with the user. */
@Composable
fun FriendsScreen(
    onOpenFriend: (String) -> Unit,
    viewModel: FriendsViewModel = hiltViewModel(),
) {
    val balances by viewModel.balances.collectAsStateWithLifecycle()
    val incoming by viewModel.incoming.collectAsStateWithLifecycle()
    val totals by viewModel.totals.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    FriendsContent(
        balances = balances,
        incoming = incoming,
        totals = totals,
        form = form,
        message = message,
        onOpenFriend = onOpenFriend,
        onAddFriend = { name, vpas, username -> viewModel.saveFriend(null, name, vpas, username) },
        onFormClosed = viewModel::formClosed,
        onMarkPaid = viewModel::markIncomingPaid,
        onMessageShown = viewModel::messageShown,
    )
}

@Composable
fun FriendsContent(
    balances: List<FriendBalance>,
    incoming: List<IncomingSplitEntity>,
    totals: FriendTotals,
    form: FriendForm,
    message: String?,
    onOpenFriend: (String) -> Unit,
    onAddFriend: (name: String, vpas: List<String>, username: String?) -> Unit,
    onFormClosed: () -> Unit,
    onMarkPaid: (String) -> Unit,
    onMessageShown: () -> Unit,
) {
    val colors = Inkless.colors
    var adding by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); onMessageShown() }
    }
    LaunchedEffect(form.savedId) {
        if (form.savedId != null) {
            adding = false
            onFormClosed()
        }
    }
    val open = incoming.filter { !it.settled }

    Surface(Modifier.fillMaxSize(), color = colors.background) {
        Box {
            Column(Modifier.fillMaxSize()) {
                InklessTopBar("Friends", icon = R.drawable.icon_users)
                LazyColumn(
                    contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.s, bottom = Spacing.xl),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xl),
                ) {
                    item(key = "headline") {
                        PageHeadline(
                            "Even with everyone.",
                            lead = "Splits and money lent or borrowed, kept per friend. Only your part of a split counts as your spending.",
                        )
                    }
                    if (balances.isNotEmpty()) item(key = "totals") { TotalsCard(totals) }
                    if (open.isNotEmpty()) {
                        item(key = "shared-header") { SectionHeader("Shared with you") }
                        items(open, key = { "in-${it.id}" }) { split -> SharedWithYouCard(split, onMarkPaid = { onMarkPaid(split.id) }) }
                    }
                    if (balances.isEmpty()) {
                        item(key = "empty") {
                            EmptyState(
                                title = "No friends yet",
                                body = "Add the people you split bills with. When you split a payment, only your part counts as your spending.",
                                icon = R.drawable.icon_users,
                            )
                        }
                    } else {
                        item(key = "friends-header") { SectionHeader("Your friends") }
                        item(key = "friends") {
                            InklessCard(padding = PaddingValues(horizontal = Spacing.l, vertical = Spacing.xs)) {
                                balances.forEachIndexed { index, balance ->
                                    if (index > 0) HorizontalDivider(color = colors.hairline)
                                    FriendRow(balance, onClick = { onOpenFriend(balance.friend.id) })
                                }
                            }
                        }
                    }
                    item(key = "add") { PrimaryButton("Add a friend", onClick = { adding = true }, icon = R.drawable.icon_user_plus) }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }

    if (adding) {
        FriendSheet(
            friend = null,
            busy = form.busy,
            error = form.error,
            onSave = onAddFriend,
            onDismiss = { adding = false; onFormClosed() },
        )
    }
}

@Composable
private fun TotalsCard(totals: FriendTotals) {
    TintCard {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("You're owed", style = MaterialTheme.typography.labelLarge, color = Inkless.colors.primary)
                Text(Money.format(totals.owedToYouPaise), style = Inkless.type.moneyMedium, color = Inkless.colors.ink)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text("You owe", style = MaterialTheme.typography.labelLarge, color = Inkless.colors.muted)
                Text(Money.format(totals.youOwePaise), style = Inkless.type.moneyMedium, color = Inkless.colors.ink)
            }
        }
    }
}

@Composable
private fun SharedWithYouCard(split: IncomingSplitEntity, onMarkPaid: () -> Unit) {
    val colors = Inkless.colors
    InklessCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            InitialTile(split.payerName, size = 40.dp)
            Column(Modifier.weight(1f)) {
                Text("${split.payerName} paid ${Money.format(split.totalPaise)}", style = MaterialTheme.typography.titleSmall, color = colors.ink)
                Text(
                    listOfNotNull(split.note, day(split.createdAt)).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Money.format(split.mySharePaise), style = Inkless.type.moneyRow, color = colors.ink)
                Text("your part", style = MaterialTheme.typography.bodySmall, color = colors.muted)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextLink("I've paid", onClick = onMarkPaid)
            Spacer(Modifier.weight(1f))
            Text("Ticks it in their app too · needs internet", style = MaterialTheme.typography.bodySmall, color = colors.muted)
        }
    }
}

@Composable
private fun FriendRow(balance: FriendBalance, onClick: () -> Unit) {
    val words = balanceWords(balance.netPaise)
    val colors = Inkless.colors
    LedgerRow(
        title = balance.friend.name,
        amount = words.amount,
        meta = listOfNotNull(
            words.words.replaceFirstChar { it.uppercase() },
            balance.friend.linkedUsername?.let { "@$it" },
        ).joinToString(" · "),
        amountColor = when (words.tone) {
            ChipTone.Good -> colors.primary
            else -> colors.ink
        },
        leading = { InitialTile(balance.friend.name, size = 40.dp) },
        onClick = onClick,
    )
}

private val DAY = DateTimeFormatter.ofPattern("d MMM")

internal fun day(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DAY)
