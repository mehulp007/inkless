package com.mehul.inkless.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestoreException
import com.mehul.inkless.auth.AuthException
import com.mehul.inkless.auth.AuthFailure
import com.mehul.inkless.auth.AuthRepository
import com.mehul.inkless.auth.Reauth
import com.mehul.inkless.breakdown.BreakdownPrefs
import com.mehul.inkless.capture.sms.SmsCapture
import com.mehul.inkless.data.repo.LedgerRepository
import com.mehul.inkless.session.Session
import com.mehul.inkless.session.SessionManager
import com.mehul.inkless.sync.BackupStatus
import com.mehul.inkless.sync.CloudBackup
import com.mehul.inkless.sync.OfflineException
import com.mehul.inkless.sync.SyncOutcome
import com.mehul.inkless.ui.auth.FormRunner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SettingsMode {
    data object None : SettingsMode
    data object Demo : SettingsMode
    data class Account(
        val username: String?,
        val email: String?,
        val emailVerified: Boolean,
        val hasPassword: Boolean,
        val hasGoogle: Boolean,
    ) : SettingsMode
}

data class CaptureSettings(val smsOn: Boolean = false, val unreadCount: Int = 0)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val sessions: SessionManager,
    private val auth: AuthRepository,
    private val smsCapture: SmsCapture,
    private val cloudBackup: CloudBackup,
    private val breakdownPrefs: BreakdownPrefs,
    repository: LedgerRepository,
) : ViewModel() {

    /** Above what amount new payments get a "break it down?" question; null when off. */
    val askAbovePaise: StateFlow<Long?> = combine(sessions.session, breakdownPrefs.changes) { session, _ ->
        (session as? Session.SignedIn)?.let { breakdownPrefs.askAbovePaise(it.user.uid) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BreakdownPrefs.DEFAULT_PAISE)

    fun setAskAbove(paise: Long?) {
        val uid = (sessions.session.value as? Session.SignedIn)?.user?.uid ?: return
        breakdownPrefs.setAskAbovePaise(uid, paise)
    }

    val backup: StateFlow<BackupStatus> = sessions.session
        .flatMapLatest { session ->
            if (session is Session.SignedIn) cloudBackup.status(session.user.uid) else flowOf(BackupStatus())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupStatus())

    private val permissionCheck = MutableStateFlow(0)

    val capture: StateFlow<CaptureSettings> =
        combine(sessions.session, repository.unparsedCount(), smsCapture.settingsChanged, permissionCheck) { session, unread, _, _ ->
            val uid = (session as? Session.SignedIn)?.user?.uid
            CaptureSettings(smsOn = uid != null && smsCapture.isEnabled(uid), unreadCount = unread)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CaptureSettings())

    /** The SMS permission can change in system settings while we're in the background. */
    fun recheckPermission() {
        permissionCheck.value++
    }

    val mode: StateFlow<SettingsMode> = sessions.session
        .map { session ->
            when (session) {
                Session.Demo -> SettingsMode.Demo
                is Session.SignedIn -> SettingsMode.Account(
                    username = session.username,
                    email = session.user.email,
                    emailVerified = session.user.emailVerified,
                    hasPassword = session.user.hasPassword,
                    hasGoogle = session.user.hasGoogle,
                )
                else -> SettingsMode.None
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsMode.None)

    /** One-off confirmations shown in a snackbar. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** Shared by the change-password and delete-account dialogs, only one is open at a time. */
    private val dialogRunner = FormRunner(viewModelScope)
    val dialogStatus = dialogRunner.status

    fun messageShown() {
        _message.value = null
    }

    fun linkGoogle(activity: Context) = act("Google account linked") { sessions.linkGoogle(activity) }

    fun resendVerification() = act("Verification email sent") { auth.sendVerificationEmail() }

    fun changePassword(current: String, new: String) =
        dialogRunner.run { auth.changePassword(current, new) }

    fun deleteWithPassword(password: String) =
        dialogRunner.run { sessions.deleteAccount(Reauth.Password(password)) }

    fun deleteWithGoogle(activity: Context) =
        dialogRunner.run { sessions.deleteAccount(sessions.googleReauth(activity)) }

    fun dialogClosed() = dialogRunner.reset()

    fun logOut() = sessions.logOut()

    fun backUpNow() = backupAction { uid ->
        when (cloudBackup.syncNow(uid)) {
            SyncOutcome.DONE -> "Backed up"
            SyncOutcome.OFFLINE -> "No internet. It'll back up once you're online"
            SyncOutcome.FAILED -> "Couldn't back up. Try again in a bit"
            SyncOutcome.BACKUP_OFF -> "Cloud backup is off"
        }
    }

    /** For log out with changes the cloud hasn't got: only logs out once they're up. */
    fun backUpAndLogOut() = backupAction { uid ->
        if (cloudBackup.syncNow(uid) == SyncOutcome.DONE) {
            sessions.logOut()
            null
        } else {
            "Couldn't back up, so you're still logged in. Try again online, or log out anyway"
        }
    }

    fun turnOffBackup() = backupAction { uid ->
        if (cloudBackup.turnOff(uid)) {
            "Backup off. The cloud copy is deleted"
        } else {
            "Backup off. The rest of the cloud copy goes next time you're online"
        }
    }

    fun turnOnBackup() = backupAction { uid ->
        when (cloudBackup.turnOn(uid)) {
            SyncOutcome.DONE -> "Backup on. Everything is backed up"
            else -> "Backup on. It'll upload once you're online"
        }
    }

    private fun backupAction(block: suspend (uid: String) -> String?) {
        val uid = (sessions.session.value as? Session.SignedIn)?.user?.uid ?: return
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            _message.value = try {
                block(uid)
            } catch (e: OfflineException) {
                "No internet. Nothing changed"
            } catch (e: FirebaseFirestoreException) {
                "Couldn't reach the cloud. Nothing changed"
            }
            _busy.value = false
        }
    }

    fun exitDemo() = sessions.exitDemo()

    private fun act(success: String, block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            _message.value = try {
                block()
                success
            } catch (e: AuthException) {
                e.failure.message.takeUnless { e.failure == AuthFailure.CANCELLED }
            }
            _busy.value = false
        }
    }
}
