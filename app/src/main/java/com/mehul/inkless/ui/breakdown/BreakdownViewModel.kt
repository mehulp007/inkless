package com.mehul.inkless.ui.breakdown

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.mehul.inkless.breakdown.Breakdown
import com.mehul.inkless.breakdown.DraftItem
import com.mehul.inkless.breakdown.ShareDrafts
import com.mehul.inkless.data.db.entity.CategoryEntity
import com.mehul.inkless.data.db.entity.TransactionEntity
import com.mehul.inkless.data.repo.LedgerRepository
import com.mehul.inkless.parsing.Amounts
import com.mehul.inkless.ui.navigation.BreakdownRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** An item line the user types an amount into. */
data class ItemLine(
    val key: Int,
    val amountText: String = "",
    val categoryId: String? = null,
    val note: String = "",
) {
    val amountPaise: Long get() = Amounts.toPaise(amountText) ?: 0
}

data class BreakdownState(
    val loading: Boolean = true,
    val transaction: TransactionEntity? = null,
    val categories: List<CategoryEntity> = emptyList(),
    /** Every item but the last, which always takes whatever is left. */
    val lines: List<ItemLine> = emptyList(),
    val lastCategoryId: String? = null,
    val lastNote: String = "",
    /** True when the lines came from a shared order. */
    val fromShare: Boolean = false,
) {
    private val total get() = transaction?.amountPaise ?: 0
    private val drafts get() = lines.map { DraftItem(it.amountPaise, it.categoryId, it.note) }

    val restPaise: Long get() = Breakdown.rest(total, drafts)
    val problem: String? get() = Breakdown.problem(total, drafts, lastCategoryId)

    fun items(): List<DraftItem>? = Breakdown.complete(total, drafts, lastCategoryId, lastNote)
}

@HiltViewModel
class BreakdownViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: LedgerRepository,
    shareDrafts: ShareDrafts,
) : ViewModel() {

    private val route = savedState.toRoute<BreakdownRoute>()
    private var nextKey = 0

    private val _state = MutableStateFlow(BreakdownState())
    val state: StateFlow<BreakdownState> = _state.asStateFlow()

    init {
        val shared = route.draft?.let(shareDrafts::take)
        viewModelScope.launch {
            val txn = repository.transaction(route.transactionId).first()
            val categories = repository.categories().first()
            val saved = repository.items(route.transactionId).first().map { DraftItem(it.amountPaise, it.categoryId, it.note) }
            // A shared order first, then the breakdown already saved, else a fresh start.
            val start = shared?.takeIf { it.isNotEmpty() } ?: saved.takeIf { it.size >= 2 }
            _state.value = if (start == null) {
                BreakdownState(loading = false, transaction = txn, categories = categories, lines = listOf(ItemLine(nextKey++)))
            } else {
                val last = start.last()
                BreakdownState(
                    loading = false,
                    transaction = txn,
                    categories = categories,
                    lines = start.dropLast(1).map { it.toLine() }.ifEmpty { listOf(ItemLine(nextKey++)) },
                    lastCategoryId = last.categoryId,
                    lastNote = last.note.orEmpty(),
                    fromShare = shared != null,
                )
            }
        }
    }

    fun setAmount(key: Int, text: String) = updateLine(key) { it.copy(amountText = text.filter { c -> c.isDigit() || c == '.' }.take(10)) }

    fun setCategory(key: Int, categoryId: String) = updateLine(key) { it.copy(categoryId = categoryId) }

    fun setNote(key: Int, note: String) = updateLine(key) { it.copy(note = note.take(60)) }

    fun addLine() = _state.update { it.copy(lines = it.lines + ItemLine(nextKey++)) }

    fun removeLine(key: Int) = _state.update { s -> s.copy(lines = s.lines.filterNot { it.key == key }) }

    fun setLastCategory(categoryId: String) = _state.update { it.copy(lastCategoryId = categoryId) }

    fun setLastNote(note: String) = _state.update { it.copy(lastNote = note.take(60)) }

    fun save(then: () -> Unit) {
        val items = _state.value.items() ?: return
        viewModelScope.launch {
            repository.saveBreakdown(route.transactionId, items)
            then()
        }
    }

    private fun updateLine(key: Int, change: (ItemLine) -> ItemLine) =
        _state.update { s -> s.copy(lines = s.lines.map { if (it.key == key) change(it) else it }) }

    private fun DraftItem.toLine() = ItemLine(
        key = nextKey++,
        amountText = if (amountPaise > 0) formatAmount(amountPaise) else "",
        categoryId = categoryId,
        note = note.orEmpty(),
    )

    private fun formatAmount(paise: Long) =
        if (paise % 100 == 0L) (paise / 100).toString() else "%d.%02d".format(paise / 100, paise % 100)
}
