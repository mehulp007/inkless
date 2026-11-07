package com.mehul.inkless.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mehul.inkless.R
import com.mehul.inkless.auth.AuthRepository
import com.mehul.inkless.ui.components.PrimaryButton
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(
    private val auth: AuthRepository,
) : ViewModel() {

    private val runner = FormRunner(viewModelScope)
    val status = runner.status

    fun send(identifier: String) = runner.run { auth.sendPasswordReset(identifier) }

    fun edit() = runner.reset()
}

@Composable
fun ForgotPasswordScreen(
    initialIdentifier: String,
    onBack: () -> Unit,
    viewModel: ForgotPasswordViewModel = hiltViewModel(),
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    var identifier by rememberSaveable { mutableStateOf(initialIdentifier) }

    AuthScaffold(title = "Reset password", onBack = onBack) {
        Text(
            "Enter your username or email and we'll send a link to reset your password.",
            style = MaterialTheme.typography.bodyLarge,
        )
        EmailField(
            value = identifier,
            onValueChange = { identifier = it; viewModel.edit() },
            label = "Username or email",
            enabled = !status.busy,
        )
        ErrorMessage(status.error)
        if (status.done) {
            SentCard()
        } else {
            PrimaryButton(
                text = "Send reset link",
                onClick = { viewModel.send(identifier) },
                enabled = identifier.isNotBlank(),
                busy = status.busy,
            )
        }
    }
}

@Composable
private fun SentCard() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(R.drawable.ic_mail), contentDescription = null)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Worded so it reads the same whether or not the account exists.
                Text(
                    "If an account exists, we've sent a reset link to the email on file.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    "Signed up with Google? Use Continue with Google instead, or follow the link to add a password.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}
