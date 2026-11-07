package com.mehul.inkless.ui.split

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mehul.inkless.R
import com.mehul.inkless.data.db.entity.SplitMethod
import com.mehul.inkless.ui.components.CategoryTile
import com.mehul.inkless.ui.components.EmptyState
import com.mehul.inkless.ui.components.InitialTile
import com.mehul.inkless.ui.components.InklessCard
import com.mehul.inkless.ui.components.InklessTextField
import com.mehul.inkless.ui.components.InklessTopBar
import com.mehul.inkless.ui.components.PillChoice
import com.mehul.inkless.ui.components.PrimaryButton
import com.mehul.inkless.ui.components.SectionHeader
import com.mehul.inkless.ui.components.TextLink
import com.mehul.inkless.ui.components.TintCard
import com.mehul.inkless.ui.components.WarmCard
import com.mehul.inkless.ui.format.Money
import com.mehul.inkless.ui.friends.FriendDialog
import com.mehul.inkless.ui.friends.FriendForm
import com.mehul.inkless.ui.theme.Inkless
import com.mehul.inkless.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Splitting a payment the user made with friends. Only the user's part counts towards their spending. */
@Composable
fun SplitScreen(onDone: () -> Unit, viewModel: SplitViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()
    SplitContent(
        state = state,
        form = form,
        onBack = onDone,
        onToggle = viewModel::toggle,
        onMethod = viewModel::setMethod,
        onType = viewModel::type,
        onAddFriend = viewModel::addFriend,
        onFormClosed = viewModel::formClosed,
        onSave = { viewModel.save(then = onDone) },
        onRemove = { viewModel.remove(then = onDone) },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SplitContent(
    state: SplitState,
    form: FriendForm,
    onBack: () -> Unit,
    onToggle: (String) -> Unit,
    onMethod: (SplitMethod) -> Unit,
    onType: (friendId: String, text: String) -> Unit,
    onAddFriend: (name: String, vpas: List<String>, username: String?) -> Unit,
    onFormClosed: () -> Unit,
    onSave: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = Inkless.colors
    var adding by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(form.savedId) {
        if (form.savedId != null) {
            adding = false
            onFormClosed()
        }
    }
    val txn = state.transaction

    Surface(Modifier.fillMaxSize(), color = colors.background) {
        Column(Modifier.fillMaxSize()) {
            InklessTopBar("Split payment", onBack = onBack)
            when {
                state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = colors.primary) }
                txn == null || state.brokenDown -> Box(Modifier.fillMaxSize().padding(Spacing.gutter), Alignment.Center) {
                    EmptyState(
                        title = if (txn == null) "This entry was deleted" else "Already broken down",
                        body = if (txn == null) "There's nothing left to split." else "This payment is broken down into items. Go back to one category to split it.",
                        icon = R.drawable.icon_users,
                    )
                }
                else -> Column(
                    Modifier
                        .fillMaxSize()
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(horizontal = Spacing.gutter, vertical = Spacing.s),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xl),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                        CategoryTile(txn.categoryId)
                        Column(Modifier.weight(1f)) {
                            Text(state.title ?: txn.payeeRaw ?: txn.vpa ?: "Payment", style = MaterialTheme.typography.titleMedium, color = colors.ink)
                            Text(
                                Instant.ofEpochMilli(txn.timestamp).atZone(ZoneId.systemDefault()).format(WHEN),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.muted,
                            )
                        }
                        Text(Money.format(txn.amountPaise), style = Inkless.type.moneyRow.copy(fontSize = 22.sp), color = colors.ink)
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                        SectionHeader("Who's in?")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            state.friends.forEach { friend ->
                                FriendPill(friend.name, picked = friend.id in state.picked, onToggle = { onToggle(friend.id) })
                            }
                            Surface(
                                shape = CircleShape,
                                color = colors.surface,
                                border = BorderStroke(1.dp, colors.hairline),
                                contentColor = colors.primary,
                                onClick = { adding = true },
                            ) {
                                Row(Modifier.heightIn(min = 40.dp).padding(horizontal = Spacing.l), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                                    Icon(painterResource(R.drawable.icon_user_plus), contentDescription = null, modifier = Modifier.size(18.dp))
                                    Text("New friend", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                        SectionHeader("How?")
                        PillChoice(
                            labels = listOf("Equally", "Amounts", "Percent"),
                            selected = SplitMethod.entries.indexOf(state.method),
                            onSelect = { onMethod(SplitMethod.entries[it]) },
                        )
                    }

                    if (state.picked.isNotEmpty()) {
                        InklessCard {
                            val shares = state.shares?.associateBy { it.friendId }.orEmpty()
                            state.picked.forEachIndexed { index, friendId ->
                                val name = state.friends.firstOrNull { it.id == friendId }?.name ?: return@forEachIndexed
                                if (index > 0) HorizontalDivider(Modifier.padding(vertical = Spacing.s), color = colors.hairline)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                                    InitialTile(name, size = 36.dp)
                                    Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = colors.ink)
                                    if (state.method == SplitMethod.EQUAL) {
                                        Text(shares[friendId]?.let { Money.format(it.sharePaise) } ?: "–", style = Inkless.type.moneyRow, color = colors.ink)
                                    } else {
                                        val percent = state.method == SplitMethod.PERCENT
                                        InklessTextField(
                                            value = state.typed[friendId].orEmpty(),
                                            onValueChange = { onType(friendId, it) },
                                            modifier = Modifier.width(if (percent) 96.dp else 120.dp),
                                            placeholder = if (percent) "%" else "0",
                                            prefix = if (percent) null else "₹",
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        )
                                        if (percent) {
                                            Text(shares[friendId]?.let { Money.format(it.sharePaise) } ?: "–", style = Inkless.type.moneyRow, color = colors.muted)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    val problem = state.problem
                    if (problem == null) {
                        TintCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Your part", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = colors.ink)
                                Text(state.myPaise?.let { Money.format(it) } ?: "–", style = Inkless.type.moneyRow.copy(fontSize = 22.sp), color = colors.ink)
                            }
                            Row(Modifier.padding(top = Spacing.s), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                                Icon(painterResource(R.drawable.icon_circle_check), contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
                                Text("Only this counts towards your spending and budget.", style = MaterialTheme.typography.bodySmall, color = colors.primary)
                            }
                        }
                    } else {
                        WarmCard { Text(problem, style = MaterialTheme.typography.bodyMedium) }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        PrimaryButton("Save split", onClick = onSave, enabled = problem == null)
                        if (state.alreadySplit) {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                TextLink("Don't split this payment", onClick = onRemove, color = colors.danger)
                            }
                        }
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
            onSave = onAddFriend,
            onDismiss = { adding = false; onFormClosed() },
        )
    }
}

@Composable
private fun FriendPill(name: String, picked: Boolean, onToggle: () -> Unit) {
    val colors = Inkless.colors
    Surface(
        shape = CircleShape,
        color = if (picked) colors.primary else colors.surface,
        contentColor = if (picked) colors.onPrimary else colors.ink,
        border = if (picked) null else BorderStroke(1.dp, colors.hairline),
        modifier = Modifier.toggleable(value = picked, role = Role.Checkbox, onValueChange = { onToggle() }),
    ) {
        Row(Modifier.heightIn(min = 40.dp).padding(start = Spacing.xs, end = Spacing.l), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            InitialTile(name, size = 32.dp)
            Text(name, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private val WHEN = DateTimeFormatter.ofPattern("d MMM · HH:mm")
