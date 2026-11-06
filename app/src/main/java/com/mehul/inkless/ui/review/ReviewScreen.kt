package com.mehul.inkless.ui.review

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mehul.inkless.R
import com.mehul.inkless.data.db.dao.TransactionListItem
import com.mehul.inkless.data.db.entity.CategoryEntity
import com.mehul.inkless.data.db.entity.Direction
import com.mehul.inkless.data.repo.LedgerRepository
import com.mehul.inkless.ui.format.Money
import com.mehul.inkless.ui.format.Titles
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val repository: LedgerRepository,
) : ViewModel() {

    /** Null while loading. */
    val items: StateFlow<List<TransactionListItem>?> = repository.needsReview()
        .map<List<TransactionListItem>, List<TransactionListItem>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val categories: StateFlow<List<CategoryEntity>> = repository.categories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _picks = MutableStateFlow<List<String>>(emptyList())
    val picks: StateFlow<List<String>> = _picks.asStateFlow()

    init {
        viewModelScope.launch { _picks.value = repository.quickPicks(QUICK_PICKS) }
    }

    fun categorize(id: String, categoryId: String) {
        viewModelScope.launch { repository.categorize(id, categoryId) }
    }

    fun keepInOther(id: String) {
        viewModelScope.launch { repository.keepAsIs(id) }
    }

    private companion object {
        const val QUICK_PICKS = 4
    }
}

/**
 * Everything Inkless couldn't file by itself. One tap answers it, and merchant memory files the
 * same merchant's other payments too, so the list usually shrinks faster than it's tapped through.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    onBack: () -> Unit,
    onOpenTransaction: (String) -> Unit,
    viewModel: ReviewViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val picks by viewModel.picks.collectAsStateWithLifecycle()
    val byId = categories.associateBy { it.id }
    val quick = picks.mapNotNull(byId::get)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(items?.size?.takeIf { it > 0 }?.let { "To sort ($it)" } ?: "To sort") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val list = items
        if (list != null && list.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), Alignment.Center) {
                Text("All sorted. ✓", style = MaterialTheme.typography.titleMedium)
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Tap what each payment was. Inkless remembers the merchant, so you're only asked once. " +
                        "Swipe one away to leave it in Other.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(list.orEmpty(), key = { it.id }) { item ->
                ReviewCard(
                    item = item,
                    quick = quick,
                    onPick = { viewModel.categorize(item.id, it) },
                    onMore = { onOpenTransaction(item.id) },
                    onKeep = { viewModel.keepInOther(item.id) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewCard(
    item: TransactionListItem,
    quick: List<CategoryEntity>,
    onPick: (String) -> Unit,
    onMore: () -> Unit,
    onKeep: () -> Unit,
) {
    val swipe = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = swipe,
        onDismiss = { onKeep() },
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.medium)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text("Keep in Other", style = MaterialTheme.typography.labelLarge)
            }
        },
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.merchantName ?: item.payeeRaw ?: item.vpa ?: Titles.fallback(item.type),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            Instant.ofEpochMilli(item.timestamp).atZone(ZoneId.systemDefault()).format(WHEN),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        (if (item.direction == Direction.CREDIT) "+" else "−") + Money.format(item.amountPaise),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    quick.forEach { category ->
                        AssistChip(onClick = { onPick(category.id) }, label = { Text("${category.emoji} ${category.name}") })
                    }
                    AssistChip(onClick = onMore, label = { Text("More…") })
                }
            }
        }
    }
}

private val WHEN = DateTimeFormatter.ofPattern("EEE, d MMM · h:mm a")
