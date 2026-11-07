package com.mehul.inkless.ui.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mehul.inkless.R
import com.mehul.inkless.auth.AuthRepository
import com.mehul.inkless.session.SessionManager
import com.mehul.inkless.ui.auth.UsernameChecker.Companion.allowsSubmit
import com.mehul.inkless.ui.components.Footnote
import com.mehul.inkless.ui.components.PrimaryButton
import com.mehul.inkless.ui.components.PrivacyNote
import com.mehul.inkless.ui.components.TextLink
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

/** After a first Google sign-in: the account still needs the name friends find it by. */
@Composable
fun ChooseUsernameScreen(
    email: String?,
    viewModel: ChooseUsernameViewModel = hiltViewModel(),
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val usernameStatus by viewModel.usernameStatus.collectAsStateWithLifecycle()
    ChooseUsernameContent(
        email = email,
        status = status,
        usernameStatus = usernameStatus,
        onUsernameChange = viewModel::onUsernameChange,
        onClaim = viewModel::claim,
        onLogOut = viewModel::logOut,
    )
}

@Composable
fun ChooseUsernameContent(
    email: String?,
    status: FormStatus,
    usernameStatus: UsernameStatus,
    onUsernameChange: (String) -> Unit,
    onClaim: (String) -> Unit,
    onLogOut: () -> Unit,
) {
    var username by rememberSaveable { mutableStateOf("") }

    AuthScaffold(
        title = "One last thing",
        onBack = null,
        headline = "Pick a name friends can find.",
        lead = "You can log in with it too, instead of your email.",
    ) {
        UsernameField(
            value = username,
            onValueChange = { username = it; onUsernameChange(it) },
            status = usernameStatus,
            enabled = !status.busy,
        )
        PrivacyNote(
            "Friends add you by this name.",
            body = "They type it to share splits with you. You can't change it later, so pick one you like.",
            icon = R.drawable.icon_users,
        )
        ErrorMessage(status.error)
        PrimaryButton(
            text = "Continue",
            onClick = { onClaim(username) },
            enabled = usernameStatus.allowsSubmit,
            busy = status.busy,
        )
        if (email != null) Footnote("Signed in as $email")
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TextLink("Use a different account", onClick = onLogOut, enabled = !status.busy)
        }
    }
}
