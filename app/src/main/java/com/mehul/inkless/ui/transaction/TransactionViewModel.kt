package com.mehul.inkless.ui.transaction

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.mehul.inkless.data.db.entity.CategoryEntity
import com.mehul.inkless.data.db.entity.MerchantEntity
import com.mehul.inkless.data.db.entity.TransactionEntity
import com.mehul.inkless.data.db.entity.TransactionItemEntity
import com.mehul.inkless.data.repo.LedgerRepository
import com.mehul.inkless.splits.FriendsRepository
import com.mehul.inkless.splits.SplitView
import com.mehul.inkless.ui.navigation.TransactionRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TransactionScreenState(
    val loading: Boolean = true,
    val transaction: TransactionEntity? = null,
    val categories: List<CategoryEntity> = emptyList(),
    val merchant: MerchantEntity? = null,
    val items: List<TransactionItemEntity> = emptyList(),
    val split: SplitView? = null,
    val friendNames: Map<String, String> = emptyMap(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: LedgerRepository,
    friends: FriendsRepository,
) : ViewModel() {

    private val id = savedState.toRoute<TransactionRoute>().id

    val state: StateFlow<TransactionScreenState> = repository.transaction(id)
        .flatMapLatest { txn ->
            val merchant = txn?.merchantId?.let(repository::merchant) ?: flowOf(null)
            combine(repository.categories(), merchant, repository.items(id), friends.split(id), friends.friends()) { categories, m, items, split, people ->
                TransactionScreenState(
                    loading = false,
                    transaction = txn?.takeUnless { it.deleted },
                    categories = categories,
                    merchant = m,
                    items = items,
                    split = split,
                    friendNames = people.associate { it.id to it.name },
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionScreenState())

    /** Renames the merchant everywhere, e.g. "q8291…@ybl" to "Tapri near Block C". */
    fun renameMerchant(name: String) {
        val merchantId = state.value.merchant?.id ?: return
        viewModelScope.launch { repository.renameMerchant(merchantId, name) }
    }

    fun save(categoryId: String?, note: String, then: () -> Unit) {
        viewModelScope.launch {
            repository.edit(id, categoryId, note)
            then()
        }
    }

    fun removeBreakdown() {
        viewModelScope.launch { repository.removeBreakdown(id) }
    }

    fun delete(then: () -> Unit) {
        viewModelScope.launch {
            repository.delete(id)
            then()
        }
    }
}
