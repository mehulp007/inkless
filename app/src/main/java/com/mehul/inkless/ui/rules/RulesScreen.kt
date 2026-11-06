package com.mehul.inkless.ui.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mehul.inkless.R
import com.mehul.inkless.categorize.RuleField
import com.mehul.inkless.categorize.RuleMatch
import com.mehul.inkless.data.db.entity.CategoryEntity
import com.mehul.inkless.data.db.entity.RuleEntity
import com.mehul.inkless.data.repo.LedgerRepository
import com.mehul.inkless.session.SessionManager
import com.mehul.inkless.ui.components.CategoryPicker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RulesViewModel @Inject constructor(
    private val repository: LedgerRepository,
    private val sessions: SessionManager,
) : ViewModel() {

    val rules: StateFlow<List<RuleEntity>> = repository.rules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> = repository.categories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun add(field: RuleField, match: RuleMatch, pattern: String, categoryId: String) {
        val owner = sessions.currentOwnerUid() ?: return
        viewModelScope.launch {
            val filed = repository.addRule(field, match, pattern, categoryId, owner)
            _message.value = when (filed) {
                0 -> "Rule added. It'll apply to new payments."
                1 -> "Rule added and 1 waiting payment filed."
                else -> "Rule added and $filed waiting payments filed."
            }
        }
    }

    fun delete(rule: RuleEntity) = viewModelScope.launch { repository.deleteRule(rule) }

    fun move(rule: RuleEntity, up: Boolean) = viewModelScope.launch { repository.moveRule(rule, up) }

    fun messageShown() {
        _message.value = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(
    onBack: () -> Unit,
    viewModel: RulesViewModel = hiltViewModel(),
) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var adding by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val byId = categories.associateBy { it.id }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rules") },
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
                text = { Text("Add rule") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item {
                Text(
                    "Rules beat everything else Inkless knows. They run top to bottom and the first match wins. " +
                        "Payments you've already categorised are never changed.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (rules.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                        Text(
                            "No rules yet. Try \"UPI ID contains canteen → Food & Canteen\".",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            itemsIndexed(rules, key = { _, rule -> rule.id }) { index, rule ->
                val category = byId[rule.categoryId]
                ListItem(
                    headlineContent = { Text(describe(rule)) },
                    supportingContent = { Text("→ ${category?.emoji.orEmpty()} ${category?.name ?: rule.categoryId}") },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { viewModel.move(rule, up = true) }, enabled = index > 0) {
                                Icon(painterResource(R.drawable.ic_arrow_up), contentDescription = "Run earlier")
                            }
                            IconButton(onClick = { viewModel.move(rule, up = false) }, enabled = index < rules.lastIndex) {
                                Icon(painterResource(R.drawable.ic_arrow_down), contentDescription = "Run later")
                            }
                            IconButton(onClick = { viewModel.delete(rule) }) {
                                Icon(painterResource(R.drawable.ic_delete), contentDescription = "Delete rule")
                            }
                        }
                    },
                )
            }
        }
    }

    if (adding) {
        AddRuleDialog(
            categories = categories,
            onAdd = { field, match, pattern, categoryId ->
                viewModel.add(field, match, pattern, categoryId)
                adding = false
            },
            onDismiss = { adding = false },
        )
    }
}

private fun describe(rule: RuleEntity): String {
    val field = when (rule.field) {
        RuleField.UPI_ID -> "UPI ID"
        RuleField.PAYEE -> "Payee name"
        RuleField.EITHER -> "UPI ID or name"
    }
    val match = when (rule.match) {
        RuleMatch.CONTAINS -> "contains"
        RuleMatch.EXACT -> "is"
        RuleMatch.REGEX -> "matches pattern"
    }
    return "$field $match \"${rule.pattern}\""
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddRuleDialog(
    categories: List<CategoryEntity>,
    onAdd: (RuleField, RuleMatch, String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var field by rememberSaveable { mutableStateOf(RuleField.EITHER) }
    var match by rememberSaveable { mutableStateOf(RuleMatch.CONTAINS) }
    var pattern by rememberSaveable { mutableStateOf("") }
    var categoryId by rememberSaveable { mutableStateOf<String?>(null) }
    val patternError = match == RuleMatch.REGEX && pattern.isNotBlank() &&
        runCatching { Regex(pattern) }.isFailure

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New rule") },
        text = {
            Column(
                Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Look at", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(RuleField.EITHER to "Either", RuleField.UPI_ID to "UPI ID", RuleField.PAYEE to "Name").forEach { (value, label) ->
                        FilterChip(selected = field == value, onClick = { field = value }, label = { Text(label) })
                    }
                }
                Text("Match when it", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(RuleMatch.CONTAINS to "Contains", RuleMatch.EXACT to "Is exactly", RuleMatch.REGEX to "Pattern").forEach { (value, label) ->
                        FilterChip(selected = match == value, onClick = { match = value }, label = { Text(label) })
                    }
                }
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it.take(100) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (match == RuleMatch.REGEX) "Pattern (regex)" else "Text") },
                    singleLine = true,
                    isError = patternError,
                    supportingText = if (patternError) ({ Text("That pattern isn't valid") }) else null,
                )
                Text("File under", style = MaterialTheme.typography.labelLarge)
                CategoryPicker(categories = categories, selectedId = categoryId, onSelect = { categoryId = it })
            }
        },
        confirmButton = {
            TextButton(
                onClick = { categoryId?.let { onAdd(field, match, pattern, it) } },
                enabled = pattern.isNotBlank() && categoryId != null && !patternError,
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
