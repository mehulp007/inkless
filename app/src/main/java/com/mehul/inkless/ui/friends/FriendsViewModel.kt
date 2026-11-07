package com.mehul.inkless.ui.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestoreException
import com.mehul.inkless.data.db.entity.FriendEntity
import com.mehul.inkless.data.db.entity.IncomingSplitEntity
import com.mehul.inkless.session.SessionManager
import com.mehul.inkless.splits.FriendBalance
import com.mehul.inkless.splits.FriendNotFound
import com.mehul.inkless.splits.FriendTotals
import com.mehul.inkless.splits.FriendsRepository
import com.mehul.inkless.sync.OfflineException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The add/edit friend dialog's progress. */
data class FriendForm(val busy: Boolean = false, val error: String? = null, val savedId: String? = null)

@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val friends: FriendsRepository,
    private val sessions: SessionManager,
) : ViewModel() {

    val balances: StateFlow<List<FriendBalance>> = friends.balances()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val incoming: StateFlow<List<IncomingSplitEntity>> = friends.incoming()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** What everyone owes you together, and what you owe them, for the card at the top. */
    val totals: StateFlow<FriendTotals> = friends.balances().map(FriendTotals::of)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FriendTotals(0, 0))

    private val _form = MutableStateFlow(FriendForm())
    val form: StateFlow<FriendForm> = _form.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        friends.refresh()
    }

    fun saveFriend(existing: FriendEntity?, name: String, vpas: List<String>, username: String?) {
        val owner = sessions.currentOwnerUid() ?: return
        _form.value = FriendForm(busy = true)
        viewModelScope.launch {
            _form.value = try {
                FriendForm(savedId = friends.saveFriend(existing?.id, name, vpas, username, owner))
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

    fun markIncomingPaid(id: String) {
        viewModelScope.launch {
            _message.value = try {
                friends.markIncomingPaid(id)
                "Marked as paid in their app too"
            } catch (e: OfflineException) {
                "No internet. Try again when you're online"
            } catch (e: FirebaseFirestoreException) {
                "Couldn't reach the cloud. Try again in a bit"
            }
        }
    }

    fun messageShown() {
        _message.value = null
    }
}
