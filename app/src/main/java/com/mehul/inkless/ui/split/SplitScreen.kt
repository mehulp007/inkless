package com.mehul.inkless.ui.split

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mehul.inkless.R
import com.mehul.inkless.data.db.entity.SplitMethod
import com.mehul.inkless.ui.format.Money
import com.mehul.inkless.ui.friends.FriendDialog

/** Splitting a payment the user made with friends. Only the user's part counts towards their spending. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SplitScreen(onDone: () -> Unit, viewModel: SplitViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()
    var adding by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(form.savedId) {
        if (form.savedId != null) {
            adding = false
            viewModel.formClosed()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.transaction?.let { "Split ${Money.format(it.amountPaise)}" } ?: "Split") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        val txn = state.transaction
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            txn == null || state.brokenDown -> Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), Alignment.Center) {
                Text(
                    if (txn == null) "This entry was deleted." else "This payment is broken down into items. Go back to one category to split it.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> Column(
                Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Who's in?", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.friends.forEach { friend ->
                        FilterChip(
                            selected = friend.id in state.picked,
                            onClick = { viewModel.toggle(friend.id) },
                            label = { Text(friend.name) },
                        )
                    }
                    AssistChip(
                        onClick = { adding = true },
                        label = { Text("New friend") },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                    )
                }

                Text("How?", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MethodChip("Equally", SplitMethod.EQUAL, state.method, viewModel::setMethod)
                    MethodChip("Amounts", SplitMethod.EXACT, state.method, viewModel::setMethod)
                    MethodChip("Percent", SplitMethod.PERCENT, state.method, viewModel::setMethod)
                }

                val shares = state.shares?.associateBy { it.friendId }.orEmpty()
                state.picked.forEach { friendId ->
                    val name = state.friends.firstOrNull { it.id == friendId }?.name ?: return@forEach
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        if (state.method != SplitMethod.EQUAL) {
                            val percent = state.method == SplitMethod.PERCENT
                            OutlinedTextField(
                                value = state.typed[friendId].orEmpty(),
                                onValueChange = { viewModel.type(friendId, it) },
                                modifier = Modifier.width(120.dp),
                                prefix = if (percent) null else ({ Text("₹") }),
                                suffix = if (percent) ({ Text("%") }) else null,
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            )
                        }
                        if (state.method != SplitMethod.EXACT) {
                            Text(
                                shares[friendId]?.let { Money.format(it.sharePaise, alwaysShowPaise = it.sharePaise % 100 != 0L) } ?: "–",
                                modifier = Modifier.padding(start = 12.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }

                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Your part", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "This is what counts towards your spending and budget.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        state.myPaise?.let { Money.format(it, alwaysShowPaise = it % 100 != 0L) } ?: "–",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                state.problem?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
                Button(
                    onClick = { viewModel.save(then = onDone) },
                    enabled = state.problem == null,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) { Text("Save split") }
                if (state.alreadySplit) {
                    TextButton(onClick = { viewModel.remove(then = onDone) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Don't split this payment", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (adding) {
        FriendDialog(
            friend = null,
            busy = form.busy,
            error = form.error,
            onSave = viewModel::addFriend,
            onDismiss = { adding = false; viewModel.formClosed() },
        )
    }
}

@Composable
private fun MethodChip(label: String, method: SplitMethod, current: SplitMethod, onPick: (SplitMethod) -> Unit) {
    FilterChip(selected = method == current, onClick = { onPick(method) }, label = { Text(label) })
}
