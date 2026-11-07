package com.mehul.inkless.ui.split

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.google.firebase.firestore.FirebaseFirestoreException
import com.mehul.inkless.data.db.entity.FriendEntity
import com.mehul.inkless.data.db.entity.SplitMethod
import com.mehul.inkless.data.db.entity.TransactionEntity
import com.mehul.inkless.data.repo.LedgerRepository
import com.mehul.inkless.parsing.Amounts
import com.mehul.inkless.session.SessionManager
import com.mehul.inkless.splits.FriendNotFound
import com.mehul.inkless.splits.FriendsRepository
import com.mehul.inkless.splits.ShareDraft
import com.mehul.inkless.splits.SplitMath
import com.mehul.inkless.ui.format.Money
import com.mehul.inkless.ui.friends.FriendForm
import com.mehul.inkless.ui.navigation.SplitRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SplitState(
    val loading: Boolean = true,
    val transaction: TransactionEntity? = null,
    val brokenDown: Boolean = false,
    val friends: List<FriendEntity> = emptyList(),
    /** Friends in the split, in the order they were picked. */
    val picked: List<String> = emptyList(),
    val method: SplitMethod = SplitMethod.EQUAL,
    /** What's typed per friend: rupees for EXACT, a percentage for PERCENT. */
    val typed: Map<String, String> = emptyMap(),
    val alreadySplit: Boolean = false,
    /** The merchant's name, when Inkless knows it. */
    val title: String? = null,
) {
    private val total get() = transaction?.amountPaise ?: 0

    /** Each friend's share, or null while what's typed doesn't add up. */
    val shares: List<ShareDraft>? get() = when (method) {
        SplitMethod.EQUAL -> SplitMath.equal(total, picked).takeIf { it.isNotEmpty() }
        SplitMethod.EXACT -> picked.associateWith { Amounts.toPaise(typed[it].orEmpty()) ?: 0 }.let { SplitMath.exact(total, it) }
        // A percentage reads like an amount with two decimals, so "33.33" is 3333 hundredths.
        SplitMethod.PERCENT -> picked.associateWith { (Amounts.toPaise(typed[it].orEmpty()) ?: 0).toInt() }.let { SplitMath.percent(total, it) }
    }

    val myPaise: Long? get() = shares?.let { SplitMath.myShare(total, it) }

    val problem: String? get() = when {
        picked.isEmpty() -> "Pick who you're splitting with."
        shares != null -> null
        method == SplitMethod.EXACT -> "Fill in an amount for everyone, adding up to no more than ${Money.format(total)}."
        else -> "Fill in a percentage for everyone, adding up to no more than 100%."
    }
}

@HiltViewModel
class SplitViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val ledger: LedgerRepository,
    private val friends: FriendsRepository,
    private val sessions: SessionManager,
) : ViewModel() {

    private val id = savedState.toRoute<SplitRoute>().transactionId

    private val _state = MutableStateFlow(SplitState())
    val state: StateFlow<SplitState> = _state.asStateFlow()

    private val _form = MutableStateFlow(FriendForm())
    val form: StateFlow<FriendForm> = _form.asStateFlow()

    init {
        viewModelScope.launch {
            val txn = ledger.transaction(id).first()
            val items = ledger.items(id).first()
            val saved = friends.split(id).first()
            _state.value = SplitState(
                loading = false,
                transaction = txn,
                brokenDown = items.isNotEmpty(),
                friends = friends.friends().first(),
                picked = saved?.shares?.map { it.friendId }.orEmpty(),
                method = saved?.split?.method ?: SplitMethod.EQUAL,
                typed = saved?.shares.orEmpty().associate { share ->
                    share.friendId to when (saved?.split?.method) {
                        SplitMethod.PERCENT -> plain(share.basisPoints?.toLong() ?: 0)
                        else -> plain(share.sharePaise)
                    }
                },
                alreadySplit = saved != null,
                title = txn?.merchantId?.let { ledger.merchant(it).first()?.displayName },
            )
        }
        // Friends added from this screen show up straight away.
        viewModelScope.launch {
            friends.friends().collect { list -> _state.update { if (it.loading) it else it.copy(friends = list) } }
        }
    }

    fun toggle(friendId: String) = _state.update {
        it.copy(picked = if (friendId in it.picked) it.picked - friendId else it.picked + friendId)
    }

    fun setMethod(method: SplitMethod) = _state.update { it.copy(method = method, typed = emptyMap()) }

    fun type(friendId: String, text: String) = _state.update { it.copy(typed = it.typed + (friendId to text.take(10))) }

    fun addFriend(name: String, vpas: List<String>, username: String?) {
        val owner = sessions.currentOwnerUid() ?: return
        _form.value = FriendForm(busy = true)
        viewModelScope.launch {
            _form.value = try {
                val newId = friends.saveFriend(null, name, vpas, username, owner)
                _state.update { it.copy(picked = it.picked + newId) }
                FriendForm(savedId = newId)
            } catch (e: FriendNotFound) {
                FriendForm(error = "No Inkless user is called @$username.")
            } catch (e: FirebaseFirestoreException) {
                FriendForm(error = "Couldn't check the username. Are you online?")
            }
        }
    }

    fun formClosed() {
        _form.value = FriendForm()
    }

    fun save(then: () -> Unit) {
        val current = _state.value
        val shares = current.shares ?: return
        val owner = sessions.currentOwnerUid() ?: return
        viewModelScope.launch {
            friends.saveSplit(id, current.method, shares, owner)
            then()
        }
    }

    fun remove(then: () -> Unit) {
        viewModelScope.launch {
            friends.removeSplit(id)
            then()
        }
    }

    // 45000 -> "450", 33333 -> "333.33": how a saved amount (or hundredths of a percent) is typed back in.
    private fun plain(hundredths: Long) =
        if (hundredths % 100 == 0L) "${hundredths / 100}" else "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')}"
}
