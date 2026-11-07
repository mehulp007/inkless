package com.mehul.inkless.ui.auth

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mehul.inkless.auth.AuthRepository
import com.mehul.inkless.session.SessionManager
import com.mehul.inkless.ui.auth.UsernameChecker.Companion.allowsSubmit
import com.mehul.inkless.ui.components.PrimaryButton
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ChooseUsernameViewModel @Inject constructor(
    private val sessions: SessionManager,
    auth: AuthRepository,
) : ViewModel() {

    private val runner = FormRunner(viewModelScope)
    val status = runner.status

    private val checker = UsernameChecker(viewModelScope, auth)
    val usernameStatus = checker.status

    fun onUsernameChange(username: String) {
        checker.onInput(username)
        runner.clearError()
    }

    fun claim(username: String) = runner.run { sessions.claimUsername(username) }

    fun logOut() = sessions.logOut()
}

@Composable
fun ChooseUsernameScreen(
    email: String?,
    viewModel: ChooseUsernameViewModel = hiltViewModel(),
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val usernameStatus by viewModel.usernameStatus.collectAsStateWithLifecycle()
    var username by rememberSaveable { mutableStateOf("") }

    AuthScaffold(title = "Pick a username", onBack = null) {
        Text(
            "Friends can find you by it, and you can log in with it instead of your email.",
            style = MaterialTheme.typography.bodyLarge,
        )
        UsernameField(
            value = username,
            onValueChange = { username = it; viewModel.onUsernameChange(it) },
            status = usernameStatus,
            enabled = !status.busy,
        )
        ErrorMessage(status.error)
        PrimaryButton(
            text = "Continue",
            onClick = { viewModel.claim(username) },
            enabled = usernameStatus.allowsSubmit,
            busy = status.busy,
        )
        if (email != null) {
            Text(
                "Signed in as $email",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = viewModel::logOut, enabled = !status.busy) {
            Text("Use a different account")
        }
    }
}
