package com.mehul.inkless.ui.auth

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mehul.inkless.auth.AuthRepository
import com.mehul.inkless.auth.AuthValidation
import com.mehul.inkless.session.SessionManager
import com.mehul.inkless.ui.auth.UsernameChecker.Companion.allowsSubmit
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SignUpViewModel @Inject constructor(
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

    fun clearError() = runner.clearError()

    fun signUp(username: String, email: String, password: String) =
        runner.run { sessions.signUp(username, email.trim(), password) }
}

@Composable
fun SignUpScreen(
    onBack: () -> Unit,
    viewModel: SignUpViewModel = hiltViewModel(),
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val usernameStatus by viewModel.usernameStatus.collectAsStateWithLifecycle()

    var username by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    val emailError = if (email.isNotBlank()) AuthValidation.emailProblem(email) else null
    val passwordProblem = if (password.isNotEmpty()) AuthValidation.passwordProblem(password) else null
    val confirmError = if (confirm.isNotEmpty() && confirm != password) "Passwords don't match" else null
    val canSubmit = usernameStatus.allowsSubmit &&
        email.isNotBlank() && emailError == null &&
        password.isNotEmpty() && passwordProblem == null &&
        confirm == password

    val submit = { if (canSubmit) viewModel.signUp(username, email, password) }

    AuthScaffold(title = "Create account", onBack = onBack) {
        UsernameField(
            value = username,
            onValueChange = { username = it; viewModel.onUsernameChange(it) },
            status = usernameStatus,
            enabled = !status.busy,
        )
        EmailField(
            value = email,
            onValueChange = { email = it; viewModel.clearError() },
            enabled = !status.busy,
            error = emailError,
            isNewAccount = true,
        )
        PasswordField(
            value = password,
            onValueChange = { password = it; viewModel.clearError() },
            enabled = !status.busy,
            isNewPassword = true,
            supporting = passwordProblem ?: "At least 8 characters, with a letter and a digit",
            imeAction = ImeAction.Next,
        )
        if (password.isNotEmpty()) StrengthHint(AuthValidation.passwordStrength(password))
        PasswordField(
            value = confirm,
            onValueChange = { confirm = it },
            label = "Confirm password",
            enabled = !status.busy,
            isNewPassword = true,
            error = confirmError,
            onDone = submit,
        )
        ErrorMessage(status.error)
        PrimaryButton(text = "Create account", onClick = submit, enabled = canSubmit, busy = status.busy)
        Text(
            "We'll email you a link to verify your address. You can use the app while you wait.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
