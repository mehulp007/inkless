package com.mehul.inkless.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MyAccountsViewModel @Inject constructor(
    private val repository: LedgerRepository,
    private val sessions: SessionManager,
) : ViewModel() {

    val accounts: StateFlow<List<MyAccountEntity>> = repository.myAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(match: AccountMatch, value: String, role: AccountRole, label: String) {
        val owner = sessions.currentOwnerUid() ?: return
        viewModelScope.launch { repository.addMyAccount(match, value, role, label, owner) }
    }

    fun remove(account: MyAccountEntity) = viewModelScope.launch { repository.removeMyAccount(account) }
}

/** The user's own accounts (transfers between them aren't spending) and home's (money from them is allowance). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyAccountsScreen(
    onBack: () -> Unit,
    viewModel: MyAccountsViewModel = hiltViewModel(),
) {
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    var adding by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My accounts") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                text = { Text("Add") },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                Text(
                    "Add your own UPI IDs (and your name as banks print it) so moving money between your accounts " +
                        "isn't counted as spending. Add home's UPI ID so money from them shows as allowance.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            for (role in AccountRole.entries) {
                val ofRole = accounts.filter { it.role == role }
                item(key = "title-$role") {
                    Text(
                        if (role == AccountRole.MINE) "Mine" else "Home",
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (ofRole.isEmpty()) {
                    item(key = "empty-$role") {
                        Text(
                            "None yet",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(ofRole, key = { it.id }) { account ->
                    ListItem(
                        headlineContent = { Text(account.label ?: account.value) },
                        supportingContent = {
                            Text((if (account.match == AccountMatch.UPI_ID) "UPI ID · " else "Name · ") + account.value)
                        },
                        trailingContent = {
                            IconButton(onClick = { viewModel.remove(account) }) {
                                Icon(painterResource(R.drawable.ic_delete), contentDescription = "Remove")
                            }
                        },
                    )
                }
            }
        }
    }

    if (adding) {
        AddAccountDialog(
            onAdd = { match, value, role, label ->
                viewModel.add(match, value, role, label)
                adding = false
            },
            onDismiss = { adding = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddAccountDialog(
    onAdd: (AccountMatch, String, AccountRole, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var role by rememberSaveable { mutableStateOf(AccountRole.MINE) }
    var match by rememberSaveable { mutableStateOf(AccountMatch.UPI_ID) }
    var value by rememberSaveable { mutableStateOf("") }
    var label by rememberSaveable { mutableStateOf("") }
    val valid = value.isNotBlank() && (match == AccountMatch.NAME || '@' in value)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = role == AccountRole.MINE, onClick = { role = AccountRole.MINE }, label = { Text("Mine") })
                    FilterChip(selected = role == AccountRole.HOME, onClick = { role = AccountRole.HOME }, label = { Text("Home") })
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = match == AccountMatch.UPI_ID, onClick = { match = AccountMatch.UPI_ID }, label = { Text("UPI ID") })
                    FilterChip(selected = match == AccountMatch.NAME, onClick = { match = AccountMatch.NAME }, label = { Text("Name") })
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.take(80) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(if (match == AccountMatch.UPI_ID) "UPI ID, e.g. you@oksbi" else "Name as banks show it") },
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(30) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Label (optional), e.g. SBI savings") },
                )
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(match, value, role, label) }, enabled = valid) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
