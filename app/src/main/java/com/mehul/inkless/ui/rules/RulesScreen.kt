package com.mehul.inkless.ui.rules

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
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
import com.mehul.inkless.ui.components.CategoryTile
import com.mehul.inkless.ui.components.EmptyState
import com.mehul.inkless.ui.components.FieldNote
import com.mehul.inkless.ui.components.InklessBottomSheet
import com.mehul.inkless.ui.components.InklessCard
import com.mehul.inkless.ui.components.InklessTextField
import com.mehul.inkless.ui.components.InklessTopBar
import com.mehul.inkless.ui.components.PageHeadline
import com.mehul.inkless.ui.components.PillChoice
import com.mehul.inkless.ui.components.PrimaryButton
import com.mehul.inkless.ui.components.TextLink
import com.mehul.inkless.ui.theme.Inkless
import com.mehul.inkless.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The rule being typed in the editor, for the "Matches N payments" line. */
data class RuleDraft(val field: RuleField, val match: RuleMatch, val pattern: String) {
    val valid: Boolean
        get() = pattern.isNotBlank() && (match != RuleMatch.REGEX || runCatching { Regex(pattern) }.isSuccess)
}

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class RulesViewModel @Inject constructor(
    private val repository: LedgerRepository,
    private val sessions: SessionManager,
) : ViewModel() {

    val rules: StateFlow<List<RuleEntity>> = repository.rules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> = repository.categories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val draft = MutableStateFlow<RuleDraft?>(null)

    /** How many payments so far the rule being typed would pick; null while there's nothing to count. */
    val matchCount: StateFlow<Int?> = draft
        .debounce(250)
        .mapLatest { d -> d?.takeIf { it.valid }?.let { runCatching { repository.ruleMatchCount(it.field, it.match, it.pattern) }.getOrNull() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun preview(draft: RuleDraft?) {
        this.draft.value = draft
    }

    /** Adds a rule, or changes [rule] when editing one. */
    fun save(rule: RuleEntity?, field: RuleField, match: RuleMatch, pattern: String, categoryId: String) {
        val owner = sessions.currentOwnerUid() ?: return
        viewModelScope.launch {
            val filed = if (rule == null) {
                repository.addRule(field, match, pattern, categoryId, owner)
            } else {
                repository.updateRule(rule, field, match, pattern, categoryId)
            }
            _message.value = RuleText.saved(added = rule == null, filed = filed)
        }
    }

    fun delete(rule: RuleEntity) = viewModelScope.launch {
        repository.deleteRule(rule)
        _message.value = "Rule deleted. Payments it filed keep their category."
    }

    fun move(rule: RuleEntity, up: Boolean) = viewModelScope.launch { repository.moveRule(rule, up) }

    fun messageShown() {
        _message.value = null
    }
}

@Composable
fun RulesScreen(
    onBack: () -> Unit,
    viewModel: RulesViewModel = hiltViewModel(),
) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val matchCount by viewModel.matchCount.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    RulesContent(
        rules = rules,
        categories = categories,
        matchCount = matchCount,
        onBack = onBack,
        onPreview = viewModel::preview,
        onSave = viewModel::save,
        onDelete = { viewModel.delete(it) },
        onMove = { rule, up -> viewModel.move(rule, up) },
        snackbar = snackbar,
    )
}

/** Which rule the editor is open for: a new one, or an existing one by id. */
private const val NEW_RULE = ""

@Composable
fun RulesContent(
    rules: List<RuleEntity>,
    categories: List<CategoryEntity>,
    matchCount: Int?,
    onBack: () -> Unit,
    onPreview: (RuleDraft?) -> Unit,
    onSave: (rule: RuleEntity?, field: RuleField, match: RuleMatch, pattern: String, categoryId: String) -> Unit,
    onDelete: (RuleEntity) -> Unit,
    onMove: (RuleEntity, up: Boolean) -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    val colors = Inkless.colors
    val byId = categories.associateBy { it.id }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }

    Surface(Modifier.fillMaxSize(), color = colors.background) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                InklessTopBar("Categories & rules", onBack = onBack)
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.s, bottom = Spacing.xl),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xl),
                ) {
                    PageHeadline(
                        "Your filing rules.",
                        lead = "Rules beat everything else Inkless knows. They run top to bottom and the first match wins. " +
                            "Payments you've already sorted are never changed.",
                    )
                    if (rules.isEmpty()) {
                        EmptyState(
                            title = "No rules yet",
                            body = "Try “UPI ID contains canteen → Food & Canteen”. Inkless also remembers every place you sort by hand.",
                            icon = R.drawable.icon_tags,
                        )
                    } else {
                        InklessCard(padding = PaddingValues(vertical = Spacing.xs)) {
                            rules.forEachIndexed { index, rule ->
                                if (index > 0) HorizontalDivider(Modifier.padding(horizontal = Spacing.l), color = colors.hairline)
                                RuleRow(
                                    rule = rule,
                                    categoryName = byId[rule.categoryId]?.name ?: "Other",
                                    canMoveUp = index > 0,
                                    canMoveDown = index < rules.lastIndex,
                                    onClick = { editing = rule.id },
                                    onMove = { up -> onMove(rule, up) },
                                )
                            }
                        }
                    }
                    PrimaryButton("Add a rule", onClick = { editing = NEW_RULE }, icon = R.drawable.icon_plus)
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        }
    }

    editing?.let { id ->
        val rule = rules.firstOrNull { it.id == id }
        val close = {
            editing = null
            onPreview(null)
        }
        RuleSheet(
            rule = rule,
            categories = categories,
            matchCount = matchCount,
            onPreview = onPreview,
            onSave = { field, match, pattern, categoryId ->
                onSave(rule, field, match, pattern, categoryId)
                close()
            },
            onDelete = rule?.let { { onDelete(it); close() } },
            onDismiss = close,
        )
    }
}

@Composable
private fun RuleRow(
    rule: RuleEntity,
    categoryName: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onMove: (up: Boolean) -> Unit,
) {
    val colors = Inkless.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = Spacing.l, top = Spacing.m, bottom = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        CategoryTile(rule.categoryId)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(RuleText.describe(rule.field, rule.match, rule.pattern), style = MaterialTheme.typography.bodyLarge, color = colors.ink)
            Text("→ $categoryName", style = MaterialTheme.typography.bodySmall, color = colors.primary)
        }
        Column {
            IconButton(onClick = { onMove(true) }, enabled = canMoveUp) {
                Icon(
                    painterResource(R.drawable.icon_chevron_down),
                    contentDescription = "Run earlier",
                    tint = if (canMoveUp) colors.muted else colors.hairline,
                    modifier = Modifier.size(18.dp).rotate(180f),
                )
            }
            IconButton(onClick = { onMove(false) }, enabled = canMoveDown) {
                Icon(
                    painterResource(R.drawable.icon_chevron_down),
                    contentDescription = "Run later",
                    tint = if (canMoveDown) colors.muted else colors.hairline,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleSheet(
    rule: RuleEntity?,
    categories: List<CategoryEntity>,
    matchCount: Int?,
    onPreview: (RuleDraft?) -> Unit,
    onSave: (RuleField, RuleMatch, String, String) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val fields = listOf(RuleField.UPI_ID, RuleField.PAYEE, RuleField.EITHER)
    val matches = listOf(RuleMatch.CONTAINS, RuleMatch.EXACT, RuleMatch.REGEX)
    var field by rememberSaveable { mutableStateOf(rule?.field ?: RuleField.EITHER) }
    var match by rememberSaveable { mutableStateOf(rule?.match ?: RuleMatch.CONTAINS) }
    var pattern by rememberSaveable { mutableStateOf(rule?.pattern ?: "") }
    var categoryId by rememberSaveable { mutableStateOf(rule?.categoryId) }
    val draft = RuleDraft(field, match, pattern)
    val badPattern = pattern.isNotBlank() && !draft.valid
    LaunchedEffect(draft) { onPreview(draft) }

    InklessBottomSheet(onDismiss) {
        Text(if (rule == null) "New rule" else "Edit rule", style = MaterialTheme.typography.headlineSmall, color = Inkless.colors.ink)
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text("Look at", style = MaterialTheme.typography.labelMedium, color = Inkless.colors.muted)
                PillChoice(listOf("UPI ID", "Name", "Either"), fields.indexOf(field), { field = fields[it] })
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text("Match when it", style = MaterialTheme.typography.labelMedium, color = Inkless.colors.muted)
                PillChoice(listOf("Contains", "Is exactly", "Pattern"), matches.indexOf(match), { match = matches[it] })
            }
            InklessTextField(
                value = pattern,
                onValueChange = { pattern = it.take(100) },
                label = if (match == RuleMatch.REGEX) "Pattern" else "Text",
                placeholder = if (match == RuleMatch.REGEX) "swiggy|zomato" else "canteen",
                note = when {
                    badPattern -> "That pattern isn't valid"
                    pattern.isBlank() || matchCount == null -> "Not case sensitive"
                    else -> RuleText.matches(matchCount)
                },
                noteKind = when {
                    badPattern -> FieldNote.Problem
                    pattern.isNotBlank() && matchCount != null && matchCount > 0 -> FieldNote.Good
                    else -> FieldNote.Plain
                },
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text("File under", style = MaterialTheme.typography.labelMedium, color = Inkless.colors.muted)
                CategoryPicker(categories = categories, selectedId = categoryId, onSelect = { categoryId = it })
            }
            PrimaryButton(
                if (rule == null) "Add rule" else "Save rule",
                onClick = { categoryId?.let { onSave(field, match, pattern, it) } },
                enabled = draft.valid && categoryId != null,
            )
            if (onDelete != null) {
                TextLink("Delete rule", onDelete, Modifier.align(Alignment.CenterHorizontally), color = Inkless.colors.danger)
            }
        }
    }
}

object RuleText {

    /** "UPI ID contains “swiggy”". */
    fun describe(field: RuleField, match: RuleMatch, pattern: String): String {
        val what = when (field) {
            RuleField.UPI_ID -> "UPI ID"
            RuleField.PAYEE -> "Name"
            RuleField.EITHER -> "UPI ID or name"
        }
        val how = when (match) {
            RuleMatch.CONTAINS -> "contains"
            RuleMatch.EXACT -> "is"
            RuleMatch.REGEX -> "matches"
        }
        return "$what $how “$pattern”"
    }

    fun matches(count: Int): String = when (count) {
        0 -> "Matches no payments yet"
        1 -> "Matches 1 payment so far"
        else -> "Matches $count payments so far"
    }

    fun saved(added: Boolean, filed: Int): String {
        val what = if (added) "Rule added" else "Rule saved"
        return when (filed) {
            0 -> "$what. It'll apply to new payments."
            1 -> "$what and 1 waiting payment filed."
            else -> "$what and $filed waiting payments filed."
        }
    }
}
