package com.mehul.inkless.ui.capture

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mehul.inkless.R
import com.mehul.inkless.data.db.entity.CategoryEntity
import com.mehul.inkless.data.db.entity.RawCaptureEntity
import com.mehul.inkless.data.repo.LedgerRepository
import com.mehul.inkless.parsing.Amounts
import com.mehul.inkless.session.SessionManager
import com.mehul.inkless.ui.components.EmptyState
import com.mehul.inkless.ui.components.InklessBottomSheet
import com.mehul.inkless.ui.components.InklessCard
import com.mehul.inkless.ui.components.InklessTopBar
import com.mehul.inkless.ui.components.PageHeadline
import com.mehul.inkless.ui.components.PrivacyNote
import com.mehul.inkless.ui.components.TextLink
import com.mehul.inkless.ui.home.QuickAddForm
import com.mehul.inkless.ui.theme.Inkless
import com.mehul.inkless.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class UnreadViewModel @Inject constructor(
    private val repository: LedgerRepository,
    private val sessions: SessionManager,
) : ViewModel() {

    /** Null while loading. */
    val captures: StateFlow<List<RawCaptureEntity>?> = repository.unparsed()
        .map<List<RawCaptureEntity>, List<RawCaptureEntity>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val categories: StateFlow<List<CategoryEntity>> = repository.categories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun dismiss(id: String) {
        viewModelScope.launch { repository.dismissUnparsed(id) }
    }

    /** Adds the payment a message was about, dated when the message came, and drops the message. */
    fun addFrom(capture: RawCaptureEntity, amountPaise: Long, categoryId: String, merchant: String?, note: String?) {
        val owner = sessions.currentOwnerUid() ?: return
        viewModelScope.launch {
            repository.addCash(amountPaise, categoryId, note, owner, merchant, at = capture.receivedAt)
            repository.dismissUnparsed(capture.id)
        }
    }
}

/** Bank messages that passed the filter but no parser could read, so none is silently lost. */
@Composable
fun UnreadScreen(
    onBack: () -> Unit,
    viewModel: UnreadViewModel = hiltViewModel(),
) {
    val captures by viewModel.captures.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    UnreadContent(
        captures = captures,
        categories = categories,
        onBack = onBack,
        onDismiss = viewModel::dismiss,
        onAdd = viewModel::addFrom,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnreadContent(
    captures: List<RawCaptureEntity>?,
    categories: List<CategoryEntity>,
    onBack: () -> Unit,
    onDismiss: (String) -> Unit,
    onAdd: (RawCaptureEntity, amountPaise: Long, categoryId: String, merchant: String?, note: String?) -> Unit,
) {
    var adding by rememberSaveable { mutableStateOf<String?>(null) }

    Surface(Modifier.fillMaxSize(), color = Inkless.colors.background) {
        Column(Modifier.fillMaxSize()) {
            InklessTopBar("Couldn't read these", onBack = onBack)
            when {
                captures == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Inkless.colors.primary) }
                captures.isEmpty() -> Box(Modifier.fillMaxSize().padding(Spacing.gutter), Alignment.Center) {
                    EmptyState(
                        title = "Nothing unread.",
                        body = "Every bank message so far was read fine. If one ever isn't, it waits here instead of being lost.",
                        icon = R.drawable.icon_check_check,
                    )
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.s, bottom = Spacing.xl),
                    verticalArrangement = Arrangement.spacedBy(Spacing.l),
                ) {
                    item(key = "headline") {
                        PageHeadline(
                            "Messages we couldn't read.",
                            lead = "They look like bank alerts, but the amount or the direction wasn't clear. If one was a payment, add it here.",
                        )
                    }
                    item(key = "privacy") {
                        PrivacyNote(
                            "Only on this phone.",
                            body = "These messages are never backed up. Dismissing one deletes it.",
                        )
                    }
                    items(captures, key = { it.id }) { capture ->
                        MessageCard(capture, onAdd = { adding = capture.id }, onDismiss = { onDismiss(capture.id) })
                    }
                }
            }
        }
    }

    // A message dismissed or added meanwhile simply leaves no sheet to show.
    adding?.let { id -> captures?.firstOrNull { it.id == id } }?.let { capture ->
        val day = Instant.ofEpochMilli(capture.receivedAt).atZone(ZoneId.systemDefault())
        InklessBottomSheet(onDismiss = { adding = null }) {
            QuickAddForm(
                categories = categories,
                snapshot = null,
                onAdd = { amount, categoryId, merchant, note ->
                    onAdd(capture, amount, categoryId, merchant, note)
                    adding = null
                },
                onClose = { adding = null },
                today = LocalDate.now(),
                focusAmount = false,
                title = "Add this payment.",
                initialAmountPaise = capture.body?.let(Amounts::findPaise),
                dateLabel = day.format(WHEN),
                saveText = "Save payment",
            )
        }
    }
}

@Composable
private fun MessageCard(capture: RawCaptureEntity, onAdd: () -> Unit, onDismiss: () -> Unit) {
    val colors = Inkless.colors
    var open by rememberSaveable(capture.id) { mutableStateOf(false) }
    InklessCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(capture.sender, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = colors.ink)
            Text(
                Instant.ofEpochMilli(capture.receivedAt).atZone(ZoneId.systemDefault()).format(WHEN),
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted,
            )
        }
        Text(
            capture.body.orEmpty(),
            Modifier
                .padding(top = Spacing.s)
                .animateContentSize()
                .clickable(onClickLabel = if (open) "Show less" else "Show all") { open = !open },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.muted,
            maxLines = if (open) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextLink("Add as a payment", onClick = onAdd)
            Spacer(Modifier.weight(1f))
            TextLink("Dismiss", onClick = onDismiss, color = colors.muted)
        }
    }
}

private val WHEN = DateTimeFormatter.ofPattern("d MMM · HH:mm")
