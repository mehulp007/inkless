package com.mehul.inkless.ui.friends

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mehul.inkless.R
import com.mehul.inkless.splits.FriendBalance
import com.mehul.inkless.ui.components.SectionHeader
import com.mehul.inkless.ui.format.Money
import com.mehul.inkless.ui.theme.LocalInklessColors

/** Who owes whom: every friend's balance, and splits friends shared with the user. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    onBack: () -> Unit,
    onOpenFriend: (String) -> Unit,
    viewModel: FriendsViewModel = hiltViewModel(),
) {
    val balances by viewModel.balances.collectAsStateWithLifecycle()
    val incoming by viewModel.incoming.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var adding by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); viewModel.messageShown() }
    }
    LaunchedEffect(form.savedId) {
        if (form.savedId != null) {
            adding = false
            viewModel.formClosed()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Friends") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                text = { Text("Friend") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
            val open = incoming.filter { !it.settled }
            if (open.isNotEmpty()) {
                item { SectionHeader("Shared with you", Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp)) }
                items(open, key = { "in-${it.id}" }) { split ->
                    ListItem(
                        headlineContent = { Text("${split.payerName} paid ${Money.format(split.totalPaise)}") },
                        supportingContent = { Text(listOfNotNull(split.note, "your share ${Money.format(split.mySharePaise)}").joinToString(" · ")) },
                        trailingContent = { TextButton(onClick = { viewModel.markIncomingPaid(split.id) }) { Text("I've paid") } },
                    )
                }
            }
            item { SectionHeader("Balances", Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp)) }
            if (balances.isEmpty()) {
                item {
                    Text(
                        "Add the friends you split bills with. Then split a payment from its details, or note down money you lent.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(balances, key = { it.friend.id }) { balance -> BalanceRow(balance, onClick = { onOpenFriend(balance.friend.id) }) }
        }
    }

    if (adding) {
        FriendDialog(
            friend = null,
            busy = form.busy,
            error = form.error,
            onSave = { name, vpas, username -> viewModel.saveFriend(null, name, vpas, username) },
            onDismiss = { adding = false; viewModel.formClosed() },
        )
    }
}

@Composable
private fun BalanceRow(balance: FriendBalance, onClick: () -> Unit) {
    val (text, color) = balanceText(balance.netPaise)
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(balance.friend.name) },
        supportingContent = balance.friend.linkedUsername?.let { { Text("@$it on Inkless") } },
        trailingContent = { Text(text, color = color, fontWeight = FontWeight.Medium) },
    )
}

/** "owes you ₹400", "you owe ₹50" or "all square", coloured to match. */
@Composable
fun balanceText(netPaise: Long): Pair<String, Color> = when {
    netPaise > 0 -> "owes you ${Money.format(netPaise)}" to LocalInklessColors.current.credit
    netPaise < 0 -> "you owe ${Money.format(-netPaise)}" to MaterialTheme.colorScheme.error
    else -> "all square" to MaterialTheme.colorScheme.onSurfaceVariant
}

