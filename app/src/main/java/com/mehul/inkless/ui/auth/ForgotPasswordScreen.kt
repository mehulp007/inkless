package com.mehul.inkless.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mehul.inkless.R
import com.mehul.inkless.auth.AuthRepository
import com.mehul.inkless.ui.components.PrimaryButton
import com.mehul.inkless.ui.components.TintCard
import com.mehul.inkless.ui.theme.Inkless
import com.mehul.inkless.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(
    private val auth: AuthRepository,
) : ViewModel() {

    private val runner = FormRunner(viewModelScope)
    val status = runner.status

    private val cooldown = ResendCooldown(viewModelScope)
    val resendIn = cooldown.secondsLeft

    fun send(identifier: String) = runner.run {
        auth.sendPasswordReset(identifier)
        cooldown.start()
    }

    fun edit() = runner.reset()
}

@Composable
fun ForgotPasswordScreen(
    initialIdentifier: String,
    onBack: () -> Unit,
    viewModel: ForgotPasswordViewModel = hiltViewModel(),
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val resendIn by viewModel.resendIn.collectAsStateWithLifecycle()
    ForgotPasswordContent(
        initialIdentifier = initialIdentifier,
        status = status,
        resendIn = resendIn,
        onBack = onBack,
        onSend = viewModel::send,
        onEdit = viewModel::edit,
    )
}

@Composable
fun ForgotPasswordContent(
    initialIdentifier: String,
    status: FormStatus,
    resendIn: Int,
    onBack: () -> Unit,
    onSend: (identifier: String) -> Unit,
    onEdit: () -> Unit,
) {
    var identifier by rememberSaveable { mutableStateOf(initialIdentifier) }
    val canSend = identifier.isNotBlank() && resendIn == 0

    AuthScaffold(
        title = "Reset password",
        onBack = onBack,
        headline = "Back in a minute.",
        lead = "Enter your username or email. We'll send a reset link to the email on the account.",
    ) {
        EmailField(
            value = identifier,
            onValueChange = { identifier = it; onEdit() },
            label = "Username or email",
            enabled = !status.busy,
            imeAction = ImeAction.Done,
        )
        ErrorMessage(status.error)
        if (status.done) SentCard()
        PrimaryButton(
            text = when {
                resendIn > 0 -> "Resend in ${ResendCooldown.format(resendIn)}"
                status.done -> "Resend link"
                else -> "Send reset link"
            },
            onClick = { onSend(identifier.trim()) },
            enabled = canSend,
            busy = status.busy,
        )
    }
}

@Composable
private fun SentCard() {
    TintCard {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Icon(painterResource(R.drawable.icon_mail), contentDescription = null, tint = Inkless.colors.primary, modifier = Modifier.size(22.dp))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                // Worded so it reads the same whether or not the account exists.
                Text(
                    "If an account exists, we've sent a link to the email on file.",
                    style = MaterialTheme.typography.titleSmall,
                    color = Inkless.colors.ink,
                )
                Text(
                    "Signed up with Google? Use Continue with Google instead, or follow the link to add a password.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Inkless.colors.muted,
                )
            }
        }
    }
}
