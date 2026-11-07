package com.mehul.inkless.ui.friends

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.google.firebase.firestore.FirebaseFirestoreException
import com.mehul.inkless.data.db.dao.ShareLine
import com.mehul.inkless.data.db.entity.AccountMatch
import com.mehul.inkless.data.db.entity.AccountRole
import com.mehul.inkless.data.db.entity.IouEntity
import com.mehul.inkless.data.repo.LedgerRepository
import com.mehul.inkless.session.Session
import com.mehul.inkless.session.SessionManager
import com.mehul.inkless.splits.FriendBalance
import com.mehul.inkless.splits.FriendNotFound
import com.mehul.inkless.splits.FriendsRepository
import com.mehul.inkless.splits.Reminder
import com.mehul.inkless.splits.Settled
import com.mehul.inkless.sync.OfflineException
import com.mehul.inkless.ui.navigation.FriendRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class FriendViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val friends: FriendsRepository,
    private val ledger: LedgerRepository,
    private val sessions: SessionManager,
) : ViewModel() {

    private val id = savedState.toRoute<FriendRoute>().friendId

    /** Null once the friend is removed (or before it loads). */
    val balance: StateFlow<FriendBalance?> = friends.balances()
        .map { all -> all.firstOrNull { it.friend.id == id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** What's already been squared up with them, newest first. */
    val settled: StateFlow<List<Settled>> = combine(friends.shareLines(), friends.ious()) { lines, ious -> Settled.of(id, lines, ious) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _form = MutableStateFlow(FriendForm())
    val form: StateFlow<FriendForm> = _form.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun addIou(amountPaise: Long, theyOwe: Boolean, note: String?) {
        val owner = sessions.currentOwnerUid() ?: return
        viewModelScope.launch { friends.addIou(id, amountPaise, theyOwe, note, owner) }
    }

    fun settleShare(line: ShareLine) {
        viewModelScope.launch { friends.settleShare(line) }
    }

    fun settleIou(iou: IouEntity) {
        viewModelScope.launch { friends.settleIou(iou) }
    }

    fun editIou(iou: IouEntity, amountPaise: Long, theyOwe: Boolean, note: String?) {
        viewModelScope.launch { friends.editIou(iou, amountPaise, theyOwe, note) }
    }

    fun deleteIou(iou: IouEntity) {
        viewModelScope.launch { friends.deleteIou(iou) }
    }

    fun settleAll() {
        viewModelScope.launch { friends.settleAll(id) }
    }

    fun edit(name: String, vpas: List<String>, username: String?) {
        val owner = sessions.currentOwnerUid() ?: return
        _form.value = FriendForm(busy = true)
        viewModelScope.launch {
            _form.value = try {
                FriendForm(savedId = friends.saveFriend(id, name, vpas, username, owner))
            } catch (e: FriendNotFound) {
                FriendForm(error = "No Inkless user is called @$username.")
            } catch (e: FirebaseFirestoreException) {
                FriendForm(error = "Couldn't check the username. Are you online?")
            }
        }
    }

    fun markIncomingPaid(splitId: String) {
        viewModelScope.launch {
            _message.value = try {
                friends.markIncomingPaid(splitId)
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

    fun formClosed() {
        _form.value = FriendForm()
    }

    fun remove(then: () -> Unit) {
        val friend = balance.value?.friend ?: return
        viewModelScope.launch {
            friends.removeFriend(friend)
            then()
        }
    }

    /** The reminder text, with a UPI link to the user's own UPI ID when they've added one under My accounts. */
    suspend fun reminder(): String? {
        val current = balance.value ?: return null
        val mine = ledger.myAccounts().first().filter { it.role == AccountRole.MINE }
        val vpa = mine.firstOrNull { it.match == AccountMatch.UPI_ID }?.value
        val name = mine.firstOrNull { it.match == AccountMatch.NAME }?.value
            ?: (sessions.session.value as? Session.SignedIn)?.username
        return Reminder.text(current.friend.name, current.openShares, current.openIous, current.netPaise, vpa, name, ZoneId.systemDefault())
    }
}
