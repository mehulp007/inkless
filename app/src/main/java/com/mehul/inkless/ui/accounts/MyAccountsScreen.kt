package com.mehul.inkless.ui.accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mehul.inkless.R
import com.mehul.inkless.data.db.entity.AccountMatch
import com.mehul.inkless.data.db.entity.AccountRole
import com.mehul.inkless.data.db.entity.MyAccountEntity
import com.mehul.inkless.data.repo.LedgerRepository
import com.mehul.inkless.session.SessionManager
import com.mehul.inkless.ui.components.IconTile
import com.mehul.inkless.ui.components.InklessBottomSheet
import com.mehul.inkless.ui.components.InklessCard
import com.mehul.inkless.ui.components.InklessTextField
import com.mehul.inkless.ui.components.InklessTopBar
import com.mehul.inkless.ui.components.PageHeadline
import com.mehul.inkless.ui.components.PillChoice
import com.mehul.inkless.ui.components.PrimaryButton
import com.mehul.inkless.ui.components.PrivacyNote
import com.mehul.inkless.ui.components.SectionHeader
import com.mehul.inkless.ui.components.TextLink
import com.mehul.inkless.ui.theme.Inkless
import com.mehul.inkless.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class MyAccountsViewModel @Inject constructor(
    private val repository: LedgerRepository,
    private val sessions: SessionManager,
) : ViewModel() {

    val accounts: StateFlow<List<MyAccountEntity>> = repository.myAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Adds an account, or changes [account] when editing one. */
    fun save(account: MyAccountEntity?, match: AccountMatch, value: String, role: AccountRole, label: String) {
        val owner = sessions.currentOwnerUid() ?: return
        viewModelScope.launch {
            if (account == null) {
                repository.addMyAccount(match, value, role, label, owner)
            } else {
                repository.editMyAccount(account, match, value, role, label)
            }
        }
    }

    fun remove(account: MyAccountEntity) = viewModelScope.launch { repository.removeMyAccount(account) }
}

/** The user's own accounts (transfers between them aren't spending) and home's (money from them is allowance). */
@Composable
fun MyAccountsScreen(
    onBack: () -> Unit,
    viewModel: MyAccountsViewModel = hiltViewModel(),
) {
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    MyAccountsContent(
        accounts = accounts,
        onBack = onBack,
        onSave = viewModel::save,
        onRemove = { viewModel.remove(it) },
    )
}

private const val NEW_ACCOUNT = ""

@Composable
fun MyAccountsContent(
    accounts: List<MyAccountEntity>,
    onBack: () -> Unit,
    onSave: (account: MyAccountEntity?, match: AccountMatch, value: String, role: AccountRole, label: String) -> Unit,
    onRemove: (MyAccountEntity) -> Unit,
) {
    val colors = Inkless.colors
    var editing by rememberSaveable { mutableStateOf<String?>(null) }

    Surface(Modifier.fillMaxSize(), color = colors.background) {
        Column(Modifier.fillMaxSize()) {
            InklessTopBar("My accounts", onBack = onBack)
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.s, bottom = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.xl),
            ) {
                PageHeadline(
                    "Money that's still yours.",
                    lead = "Add your own UPI IDs, and your name as banks print it, so moving money between your accounts " +
                        "isn't counted as spending. Add home's so money from them shows as allowance.",
                )
                AccountRole.entries.forEach { role ->
                    val ofRole = accounts.filter { it.role == role }
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                        SectionHeader(AccountText.section(role))
                        InklessCard(padding = PaddingValues(vertical = Spacing.xs)) {
                            if (ofRole.isEmpty()) {
                                Text(
                                    AccountText.empty(role),
                                    Modifier.padding(horizontal = Spacing.l, vertical = Spacing.l),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.muted,
                                )
                            }
                            ofRole.forEachIndexed { index, account ->
                                if (index > 0) HorizontalDivider(Modifier.padding(horizontal = Spacing.l), color = colors.hairline)
                                AccountRow(account, onClick = { editing = account.id })
                            }
                        }
                    }
                }
                PrimaryButton("Add an account", onClick = { editing = NEW_ACCOUNT }, icon = R.drawable.icon_plus)
                PrivacyNote(
                    "Past payments follow along.",
                    body = "Whenever this list changes, Inkless re-checks your payments, so transfers and allowance are marked right away.",
                    icon = R.drawable.icon_refresh_cw,
                )
            }
        }
    }

    editing?.let { id ->
        val account = accounts.firstOrNull { it.id == id }
        AccountSheet(
            account = account,
            onSave = { match, value, role, label ->
                onSave(account, match, value, role, label)
                editing = null
            },
            onRemove = account?.let { { onRemove(it); editing = null } },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun AccountRow(account: MyAccountEntity, onClick: () -> Unit) {
    val colors = Inkless.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.l, vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        IconTile(if (account.role == AccountRole.HOME) R.drawable.icon_house else R.drawable.icon_landmark)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(account.label ?: account.value, style = MaterialTheme.typography.bodyLarge, color = colors.ink)
            Text(AccountText.meta(account), style = MaterialTheme.typography.bodySmall, color = colors.muted)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountSheet(
    account: MyAccountEntity?,
    onSave: (AccountMatch, String, AccountRole, String) -> Unit,
    onRemove: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val roles = listOf(AccountRole.MINE, AccountRole.HOME)
    val matches = listOf(AccountMatch.UPI_ID, AccountMatch.NAME)
    var role by rememberSaveable { mutableStateOf(account?.role ?: AccountRole.MINE) }
    var match by rememberSaveable { mutableStateOf(account?.match ?: AccountMatch.UPI_ID) }
    var value by rememberSaveable { mutableStateOf(account?.value ?: "") }
    var label by rememberSaveable { mutableStateOf(account?.label ?: "") }
    val problem = AccountText.problem(match, value)

    InklessBottomSheet(onDismiss) {
        Text(if (account == null) "Add an account" else "Edit account", style = MaterialTheme.typography.headlineSmall, color = Inkless.colors.ink)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text("Whose", style = MaterialTheme.typography.labelMedium, color = Inkless.colors.muted)
            PillChoice(listOf("Mine", "Home"), roles.indexOf(role), { role = roles[it] })
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text("Match by", style = MaterialTheme.typography.labelMedium, color = Inkless.colors.muted)
            PillChoice(listOf("UPI ID", "Name"), matches.indexOf(match), { match = matches[it] })
        }
        InklessTextField(
            value = value,
            onValueChange = { value = it.take(80) },
            label = if (match == AccountMatch.UPI_ID) "UPI ID" else "Name as banks show it",
            placeholder = if (match == AccountMatch.UPI_ID) "you@oksbi" else "RAHUL SHARMA",
            note = problem ?: if (role == AccountRole.HOME) "Money from here counts as allowance" else "Transfers to and from here aren't spending",
            noteKind = if (problem != null) com.mehul.inkless.ui.components.FieldNote.Problem else com.mehul.inkless.ui.components.FieldNote.Plain,
        )
        InklessTextField(
            value = label,
            onValueChange = { label = it.take(30) },
            label = "Label (optional)",
            placeholder = if (role == AccountRole.HOME) "Papa's SBI" else "SBI savings",
        )
        PrimaryButton(
            if (account == null) "Add account" else "Save account",
            onClick = { onSave(match, value, role, label) },
            enabled = value.isNotBlank() && problem == null,
        )
        if (onRemove != null) {
            TextLink("Remove account", onRemove, Modifier.align(Alignment.CenterHorizontally), color = Inkless.colors.danger)
        }
    }
}

object AccountText {
    fun section(role: AccountRole) = when (role) {
        AccountRole.MINE -> "Mine — transfers aren't spending"
        AccountRole.HOME -> "Home — money in is allowance"
    }

    fun empty(role: AccountRole) = when (role) {
        AccountRole.MINE -> "None yet. Add the UPI IDs of your own bank accounts."
        AccountRole.HOME -> "None yet. Add the UPI ID your allowance comes from."
    }

    fun meta(account: MyAccountEntity) =
        (if (account.match == AccountMatch.UPI_ID) "UPI ID" else "Name") + if (account.label != null) " · ${account.value}" else ""

    /** Why [value] can't be saved yet; null when it can (or is still empty). */
    fun problem(match: AccountMatch, value: String): String? =
        if (match == AccountMatch.UPI_ID && value.isNotBlank() && '@' !in value) "A UPI ID has an @ in it, like you@oksbi" else null
}
