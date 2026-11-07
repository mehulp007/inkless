package com.mehul.inkless.ui.auth

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mehul.inkless.R
import com.mehul.inkless.session.SessionManager
import com.mehul.inkless.ui.components.PasswordField
import com.mehul.inkless.ui.components.PrimaryButton
import com.mehul.inkless.ui.components.PrivacyNote
import com.mehul.inkless.ui.components.TextLink
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val sessions: SessionManager,
) : ViewModel() {

    private val runner = FormRunner(viewModelScope)
    val status = runner.status

    fun logIn(identifier: String, password: String) = runner.run { sessions.logIn(identifier, password) }

    fun continueWithGoogle(activity: Context) = runner.run { sessions.signInWithGoogle(activity) }

    fun clearError() = runner.clearError()
}

@Composable
fun LoginScreen(
    onBack: () -> Unit,
    onForgotPassword: (identifier: String) -> Unit,
    onSignUp: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LoginContent(
        status = status,
        onBack = onBack,
        onLogIn = viewModel::logIn,
        onGoogle = { viewModel.continueWithGoogle(context) },
        onForgotPassword = onForgotPassword,
        onSignUp = onSignUp,
        onEdit = viewModel::clearError,
    )
}

@Composable
fun LoginContent(
    status: FormStatus,
    onBack: () -> Unit,
    onLogIn: (identifier: String, password: String) -> Unit,
    onGoogle: () -> Unit,
    onForgotPassword: (identifier: String) -> Unit,
    onSignUp: () -> Unit,
    onEdit: () -> Unit = {},
) {
    var identifier by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val canSubmit = identifier.isNotBlank() && password.isNotEmpty()

    AuthScaffold(
        title = "Your Inkless account",
        onBack = onBack,
        headline = "Good to have you back.",
        lead = "Log in to pick up where you left off.",
    ) {
        EmailField(
            value = identifier,
            onValueChange = { identifier = it; onEdit() },
            label = "Username or email",
            enabled = !status.busy,
        )
        PasswordField(
            value = password,
            onValueChange = { password = it; onEdit() },
            enabled = !status.busy,
            onDone = { if (canSubmit) onLogIn(identifier, password) },
        )
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            TextLink("Forgot password?", onClick = { onForgotPassword(identifier.trim()) })
        }
        ErrorMessage(status.error)
        PrimaryButton(
            text = "Log in",
            onClick = { onLogIn(identifier, password) },
            enabled = canSubmit,
            busy = status.busy,
        )
        OrDivider()
        GoogleButton(onClick = onGoogle, enabled = !status.busy)
        PrivacyNote(
            "Your phone is your ledger.",
            body = "If backup is on, your parsed expenses come back after you log in. Raw messages stay on your phone.",
            icon = R.drawable.icon_cloud_check,
        )
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TextLink("New here? Create an account", onClick = onSignUp, enabled = !status.busy)
        }
    }
}
