package com.mehul.inkless.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mehul.inkless.auth.AuthException
import com.mehul.inkless.auth.AuthFailure
import com.mehul.inkless.auth.AuthRepository
import com.mehul.inkless.auth.Reauth
import com.mehul.inkless.session.Session
import com.mehul.inkless.session.SessionManager
import com.mehul.inkless.ui.auth.FormRunner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val sessions: SessionManager,
    private val auth: AuthRepository,
) : ViewModel() {

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
