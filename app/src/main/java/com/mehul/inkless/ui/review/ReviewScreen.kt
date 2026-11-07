package com.mehul.inkless.ui.review

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mehul.inkless.R
import com.mehul.inkless.data.db.DatabaseProvider
import com.mehul.inkless.data.db.dao.TransactionListItem
import com.mehul.inkless.data.db.entity.CaptureSource
import com.mehul.inkless.data.db.entity.CategoryEntity
import com.mehul.inkless.data.db.entity.Direction
import com.mehul.inkless.data.repo.LedgerRepository
import com.mehul.inkless.ml.CategoryModel
import com.mehul.inkless.ui.components.CategoryPicker
import com.mehul.inkless.ui.components.CategoryTile
import com.mehul.inkless.ui.components.EmptyState
import com.mehul.inkless.ui.components.InklessBottomSheet
import com.mehul.inkless.ui.components.InklessCard
import com.mehul.inkless.ui.components.InklessTopBar
import com.mehul.inkless.ui.components.PageHeadline
import com.mehul.inkless.ui.components.PrivacyNote
import com.mehul.inkless.ui.components.StatusChip
import com.mehul.inkless.ui.components.TextLink
import com.mehul.inkless.ui.format.Money
import com.mehul.inkless.ui.format.Titles
import com.mehul.inkless.ui.theme.Inkless
import com.mehul.inkless.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One payment waiting for a category, with the three likeliest answers first. */
data class SortCard(val item: TransactionListItem, val suggestions: List<CategoryEntity>)

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val repository: LedgerRepository,
    private val model: CategoryModel,
    private val databases: DatabaseProvider,
) : ViewModel() {

    val categories: StateFlow<List<CategoryEntity>> = repository.categories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Null while loading. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val cards: StateFlow<List<SortCard>?> = combine(repository.needsReview(), repository.categories()) { items, categories -> items to categories }
        .mapLatest { (items, categories) ->
            val byId = categories.associateBy { it.id }
            val picks = repository.quickPicks(SUGGESTIONS + 1)
            val db = databases.current.value
            items.map { item ->
                // The model's likely answers first, as long as they're at least plausible, then
                // the categories used most lately.
                val guessed = db?.let { model.guesses(it, item.vpa, item.payeeRaw, item.amountPaise, item.timestamp) }
                    .orEmpty()
                    .filter { it.confidence >= PLAUSIBLE }
                    .map { it.categoryId }
                val ids = (guessed + picks).distinct().filter { it != item.categoryId || guessed.contains(it) }
                SortCard(item, ids.mapNotNull(byId::get).take(SUGGESTIONS))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun categorize(id: String, categoryId: String) {
        viewModelScope.launch { repository.categorize(id, categoryId) }
    }

    fun keepInOther(id: String) {
        viewModelScope.launch { repository.keepAsIs(id) }
    }

    private companion object {
        const val SUGGESTIONS = 3
        const val PLAUSIBLE = 0.15f
    }
}

/**
 * Everything Inkless couldn't file by itself. One tap answers it, and merchant memory files the
 * same merchant's other payments too, so the list usually shrinks faster than it's tapped through.
 */
@Composable
fun ReviewScreen(
    onOpenTransaction: (String) -> Unit,
    viewModel: ReviewViewModel = hiltViewModel(),
) {
    val cards by viewModel.cards.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    ReviewContent(
        cards = cards,
        categories = categories,
        onPick = viewModel::categorize,
        onKeep = viewModel::keepInOther,
        onOpenTransaction = onOpenTransaction,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewContent(
    cards: List<SortCard>?,
    categories: List<CategoryEntity>,
    onPick: (id: String, categoryId: String) -> Unit,
    onKeep: (id: String) -> Unit,
    onOpenTransaction: (String) -> Unit,
) {
    var choosingFor by rememberSaveable { mutableStateOf<String?>(null) }

    Surface(Modifier.fillMaxSize(), color = Inkless.colors.background) {
        Column(Modifier.fillMaxSize()) {
            InklessTopBar("To sort", icon = R.drawable.icon_book_open)
            when {
                cards == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Inkless.colors.primary) }
                cards.isEmpty() -> Box(Modifier.fillMaxSize().padding(Spacing.gutter), Alignment.Center) {
                    EmptyState(
                        title = "All sorted.",
                        body = "Payments Inkless can't place by itself wait here, under Other, until you say what they were.",
                        icon = R.drawable.icon_check_check,
                    )
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.s, bottom = Spacing.xl),
                    verticalArrangement = Arrangement.spacedBy(Spacing.l),
                ) {
                    item(key = "headline") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PageHeadline(
                                "A place for everything.",
                                Modifier.weight(1f),
                                lead = "${if (cards.size == 1) "This payment is" else "These ${cards.size} payments are"} already in your spending. " +
                                    "They're under Other until you choose a category.",
                            )
                        }
                    }
                    item(key = "left") { StatusChip("${cards.size} left") }
                    items(cards, key = { it.item.id }) { card ->
                        SortCardView(
                            card = card,
                            onPick = { onPick(card.item.id, it) },
                            onAll = { choosingFor = card.item.id },
                            onView = { onOpenTransaction(card.item.id) },
                            onKeep = { onKeep(card.item.id) },
                        )
                    }
                    item(key = "note") {
                        PrivacyNote(
                            "Asked once. Remembered next time.",
                            body = "Inkless keeps your choice for this merchant on your phone. Change it any time in Settings → Categorising rules.",
                            icon = R.drawable.icon_bookmark_check,
                        )
                    }
                }
            }
        }
    }

    choosingFor?.let { id ->
        InklessBottomSheet(onDismiss = { choosingFor = null }) {
            Text("What was this for?", style = MaterialTheme.typography.titleLarge, color = Inkless.colors.ink)
            CategoryPicker(categories = categories, selectedId = null, onSelect = { onPick(id, it); choosingFor = null })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortCardView(card: SortCard, onPick: (String) -> Unit, onAll: () -> Unit, onView: () -> Unit, onKeep: () -> Unit) {
    val colors = Inkless.colors
    val item = card.item
    val swipe = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = swipe,
        onDismiss = { onKeep() },
        backgroundContent = {
            Surface(Modifier.fillMaxSize(), shape = MaterialTheme.shapes.large, color = colors.warm, contentColor = colors.onWarm) {
                Box(Modifier.padding(horizontal = Spacing.xl), contentAlignment = Alignment.CenterStart) {
                    Text("Leave it in Other", style = MaterialTheme.typography.labelLarge)
                }
            }
        },
    ) {
        InklessCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                CategoryTile(null)
                Column(Modifier.weight(1f)) {
                    Text(
                        item.merchantName ?: item.payeeRaw ?: item.vpa ?: Titles.fallback(item.type),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(sortMeta(item), style = MaterialTheme.typography.bodySmall, color = colors.muted)
                }
                Text(
                    (if (item.direction == Direction.CREDIT) "+" else "") + Money.format(item.amountPaise),
                    style = Inkless.type.moneyRow.copy(fontSize = 22.sp),
                    color = colors.ink,
                )
            }
            Text(
                "What was this for? Tap once to file it and remember this merchant.",
                Modifier.padding(top = Spacing.m, bottom = Spacing.s),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted,
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                card.suggestions.forEach { category ->
                    OutlinedButton(
                        onClick = { onPick(category.id) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                        shape = CircleShape,
                        border = BorderStroke(1.dp, colors.hairline),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = colors.surface, contentColor = colors.primary),
                    ) {
                        Text(category.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Row(Modifier.padding(top = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                TextLink("All categories", onClick = onAll)
                Spacer(Modifier.weight(1f))
                TextLink("View payment →", onClick = onView, color = colors.muted)
            }
        }
    }
}

/** "6 Nov · 17:40 · Bank SMS" */
internal fun sortMeta(item: TransactionListItem, zone: ZoneId = ZoneId.systemDefault()): String {
    val at = Instant.ofEpochMilli(item.timestamp).atZone(zone).format(WHEN)
    val source = when {
        CaptureSource.SMS in item.sources && CaptureSource.NOTIFICATION in item.sources -> "Bank SMS + notification"
        CaptureSource.SMS in item.sources -> "Bank SMS"
        CaptureSource.NOTIFICATION in item.sources -> "Notification"
        else -> null
    }
    return listOfNotNull(at, source).joinToString(" · ")
}

private val WHEN = DateTimeFormatter.ofPattern("d MMM · HH:mm")
