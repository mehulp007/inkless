package com.mehul.inkless.ui.transaction

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.mehul.inkless.data.db.entity.CategoryEntity
import com.mehul.inkless.data.db.entity.TransactionEntity
import com.mehul.inkless.data.repo.LedgerRepository
import com.mehul.inkless.ui.navigation.TransactionRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TransactionScreenState(
    val loading: Boolean = true,
    val transaction: TransactionEntity? = null,
    val categories: List<CategoryEntity> = emptyList(),
)

@HiltViewModel
class TransactionViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: LedgerRepository,
) : ViewModel() {

    private val id = savedState.toRoute<TransactionRoute>().id

    val state: StateFlow<TransactionScreenState> =
        combine(repository.transaction(id), repository.categories()) { txn, categories ->
            TransactionScreenState(loading = false, transaction = txn?.takeUnless { it.deleted }, categories = categories)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionScreenState())

    fun save(categoryId: String?, note: String, then: () -> Unit) {
        viewModelScope.launch {
            repository.edit(id, categoryId, note)
            then()
        }
    }

    fun delete(then: () -> Unit) {
        viewModelScope.launch {
            repository.delete(id)
            then()
        }
    }
}
