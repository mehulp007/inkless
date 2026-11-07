package com.mehul.inkless.ui.auth

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.mehul.inkless.session.SessionManager
import com.mehul.inkless.ui.components.PasswordField
import com.mehul.inkless.ui.components.PrimaryButton
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
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var identifier by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val canSubmit = identifier.isNotBlank() && password.isNotEmpty()

    AuthScaffold(title = "Log in", onBack = onBack) {
        EmailField(
            value = identifier,
            onValueChange = { identifier = it; viewModel.clearError() },
            label = "Username or email",
            enabled = !status.busy,
        )
        PasswordField(
            value = password,
            onValueChange = { password = it; viewModel.clearError() },
            enabled = !status.busy,
            onDone = { if (canSubmit) viewModel.logIn(identifier, password) },
        )
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            TextButton(onClick = { onForgotPassword(identifier.trim()) }) { Text("Forgot password?") }
        }
        ErrorMessage(status.error)
        PrimaryButton(
            text = "Log in",
            onClick = { viewModel.logIn(identifier, password) },
            enabled = canSubmit,
            busy = status.busy,
        )
        OrDivider()
        GoogleButton(onClick = { viewModel.continueWithGoogle(context) }, enabled = !status.busy)
    }
}
