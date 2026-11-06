package com.mehul.inkless.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.mehul.inkless.R
import com.mehul.inkless.auth.PasswordStrength
import com.mehul.inkless.ui.theme.LocalInklessColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScaffold(
    title: String,
    onBack: (() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
fun UsernameField(
    value: String,
    onValueChange: (String) -> Unit,
    status: UsernameStatus,
    enabled: Boolean,
) {
    val credit = LocalInklessColors.current.credit
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.lowercase().filterNot(Char::isWhitespace)) },
        modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.NewUsername },
        label = { Text("Username") },
        prefix = { Text("@") },
        singleLine = true,
        enabled = enabled,
        isError = status is UsernameStatus.Invalid && value.length >= 3 || status == UsernameStatus.Taken,
        trailingIcon = {
            when (status) {
                UsernameStatus.Checking -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                UsernameStatus.Available ->
                    Icon(painterResource(R.drawable.ic_check_circle), contentDescription = "Available", tint = credit)
                else -> Unit
            }
        },
        supportingText = {
            val text = when (status) {
                UsernameStatus.Empty -> "3–20 characters: lowercase letters, digits and _"
                is UsernameStatus.Invalid -> status.reason
                UsernameStatus.Checking -> "Checking…"
                UsernameStatus.Available -> "@$value is free"
                UsernameStatus.Taken -> "@$value is taken"
                UsernameStatus.Unknown -> "Couldn't check right now"
            }
            Text(text)
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
    )
}

@Composable
fun EmailField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    label: String = "Email",
    error: String? = null,
    imeAction: ImeAction = ImeAction.Next,
    isNewAccount: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().semantics {
            contentType = if (isNewAccount) ContentType.EmailAddress else ContentType.Username + ContentType.EmailAddress
        },
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = imeAction),
    )
}

@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    label: String = "Password",
    isNewPassword: Boolean = false,
    error: String? = null,
    supporting: String? = null,
    imeAction: ImeAction = ImeAction.Done,
    onDone: () -> Unit = {},
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().semantics {
            contentType = if (isNewPassword) ContentType.NewPassword else ContentType.Password
        },
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        isError = error != null,
        supportingText = (error ?: supporting)?.let { { Text(it) } },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    painterResource(if (visible) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                    contentDescription = if (visible) "Hide password" else "Show password",
                )
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
    )
}

@Composable
fun StrengthHint(strength: PasswordStrength) {
    val (label, color) = when (strength) {
        PasswordStrength.WEAK -> "Weak" to MaterialTheme.colorScheme.error
        PasswordStrength.OKAY -> "Okay" to MaterialTheme.colorScheme.tertiary
        PasswordStrength.STRONG -> "Strong" to LocalInklessColors.current.credit
    }
    Text("Strength: $label", style = MaterialTheme.typography.labelMedium, color = color)
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, enabled: Boolean, busy: Boolean) {
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
        } else {
            Text(text)
        }
    }
}

@Composable
fun GoogleButton(onClick: () -> Unit, enabled: Boolean, text: String = "Continue with Google") {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) {
        Text(text)
    }
}

@Composable
fun ErrorMessage(message: String?) {
    if (message.isNullOrEmpty()) return
    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

@Composable
fun OrDivider() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        HorizontalDivider(Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text("or", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        HorizontalDivider(Modifier.weight(1f))
    }
}
