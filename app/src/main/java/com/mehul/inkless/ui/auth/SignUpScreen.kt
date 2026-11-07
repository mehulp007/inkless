package com.mehul.inkless.ui.auth

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
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mehul.inkless.auth.AuthRepository
import com.mehul.inkless.auth.AuthValidation
import com.mehul.inkless.auth.PasswordStrength
import com.mehul.inkless.session.SessionManager
import com.mehul.inkless.ui.auth.UsernameChecker.Companion.allowsSubmit
import com.mehul.inkless.ui.components.Footnote
import com.mehul.inkless.ui.components.PasswordField
import com.mehul.inkless.ui.components.PrimaryButton
import com.mehul.inkless.ui.components.TextLink
import com.mehul.inkless.ui.components.ToggleRow
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

    fun signUp(username: String, email: String, password: String, backup: Boolean) =
        runner.run { sessions.signUp(username, email.trim(), password, backup) }
}

@Composable
fun SignUpScreen(
    onBack: () -> Unit,
    onLogIn: () -> Unit,
    viewModel: SignUpViewModel = hiltViewModel(),
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val usernameStatus by viewModel.usernameStatus.collectAsStateWithLifecycle()
    SignUpContent(
        status = status,
        usernameStatus = usernameStatus,
        onBack = onBack,
        onLogIn = onLogIn,
        onUsernameChange = viewModel::onUsernameChange,
        onEdit = viewModel::clearError,
        onSignUp = viewModel::signUp,
    )
}

@Composable
fun SignUpContent(
    status: FormStatus,
    usernameStatus: UsernameStatus,
    onBack: () -> Unit,
    onLogIn: () -> Unit,
    onUsernameChange: (String) -> Unit,
    onEdit: () -> Unit,
    onSignUp: (username: String, email: String, password: String, backup: Boolean) -> Unit,
) {
    var username by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var backup by rememberSaveable { mutableStateOf(true) }

    val emailError = if (email.isNotBlank()) AuthValidation.emailProblem(email) else null
    val passwordProblem = if (password.isNotEmpty()) AuthValidation.passwordProblem(password) else null
    val confirmError = if (confirm.isNotEmpty() && confirm != password) "Passwords don't match" else null
    val canSubmit = usernameStatus.allowsSubmit &&
        email.isNotBlank() && emailError == null &&
        password.isNotEmpty() && passwordProblem == null &&
        confirm == password

    val submit = { if (canSubmit) onSignUp(username, email, password, backup) }

    AuthScaffold(
        title = "Make yourself at home",
        onBack = onBack,
        headline = "A little less to track.",
        lead = "Create your account. Keep using Inkless while you verify your email.",
    ) {
        UsernameField(
            value = username,
            onValueChange = { username = it; onUsernameChange(it) },
            status = usernameStatus,
            enabled = !status.busy,
        )
        EmailField(
            value = email,
            onValueChange = { email = it; onEdit() },
            enabled = !status.busy,
            error = emailError,
            isNewAccount = true,
        )
        PasswordField(
            value = password,
            onValueChange = { password = it; onEdit() },
            enabled = !status.busy,
            isNewPassword = true,
            supporting = passwordProblem ?: passwordHint(password),
            imeAction = ImeAction.Next,
        )
        PasswordField(
            value = confirm,
            onValueChange = { confirm = it },
            label = "Confirm password",
            enabled = !status.busy,
            isNewPassword = true,
            error = confirmError,
            onDone = submit,
        )
        ToggleRow(
            title = "Cloud backup",
            subtitle = "Only parsed ledger data. Raw SMS stays on this phone.",
            checked = backup,
            onCheckedChange = { backup = it },
            enabled = !status.busy,
        )
        ErrorMessage(status.error)
        PrimaryButton(text = "Create account", onClick = submit, enabled = canSubmit, busy = status.busy)
        Footnote("We'll email you a link to verify your address. No ads or analytics.")
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TextLink("Already have an account? Log in", onClick = onLogIn, enabled = !status.busy)
        }
    }
}

private fun passwordHint(password: String): String {
    val rule = "8+ characters, with a letter and a number"
    if (password.isEmpty()) return rule
    val strength = when (AuthValidation.passwordStrength(password)) {
        PasswordStrength.WEAK -> "Weak"
        PasswordStrength.OKAY -> "Okay"
        PasswordStrength.STRONG -> "Strong"
    }
    return "$strength · $rule"
}
