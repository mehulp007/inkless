package com.mehul.inkless.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mehul.inkless.R
import com.mehul.inkless.ui.components.FieldNote
import com.mehul.inkless.ui.components.InklessTextField
import com.mehul.inkless.ui.components.InklessTopBar
import com.mehul.inkless.ui.components.PageHeadline
import com.mehul.inkless.ui.components.SecondaryButton
import com.mehul.inkless.ui.theme.Inkless
import com.mehul.inkless.ui.theme.Spacing

@Composable
fun AuthScaffold(
    title: String,
    onBack: (() -> Unit)?,
    headline: String? = null,
    lead: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        containerColor = Inkless.colors.background,
        topBar = { InklessTopBar(title, onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter, vertical = Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            if (headline != null) PageHeadline(headline, Modifier.padding(top = Spacing.s, bottom = Spacing.xs), lead = lead)
            content()
        }
    }
}

@Composable
fun UsernameField(
    value: String,
    onValueChange: (String) -> Unit,
    status: UsernameStatus,
    enabled: Boolean,
) {
    val note = when (status) {
        UsernameStatus.Empty -> "3–20 characters: lowercase letters, digits and _"
        is UsernameStatus.Invalid -> status.reason
        UsernameStatus.Checking -> "Checking…"
        UsernameStatus.Available -> "Available · letters, numbers and underscores"
        UsernameStatus.Taken -> "@$value is taken"
        UsernameStatus.Unknown -> "Couldn't check right now"
    }
    val problem = status is UsernameStatus.Invalid && value.length >= 3 || status == UsernameStatus.Taken
    InklessTextField(
        value = value,
        onValueChange = { onValueChange(it.lowercase().filterNot(Char::isWhitespace)) },
        modifier = Modifier.semantics { contentType = ContentType.NewUsername },
        label = "Username",
        note = note,
        noteKind = when {
            problem -> FieldNote.Problem
            status == UsernameStatus.Available -> FieldNote.Good
            else -> FieldNote.Plain
        },
        trailingIcon = if (status == UsernameStatus.Available) R.drawable.icon_circle_check else null,
        trailingDescription = "Available",
        trailingContent = if (status == UsernameStatus.Checking) {
            { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Inkless.colors.primary) }
        } else {
            null
        },
        enabled = enabled,
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
    InklessTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.semantics {
            contentType = if (isNewAccount) ContentType.EmailAddress else ContentType.Username + ContentType.EmailAddress
        },
        label = label,
        note = error,
        noteKind = if (error != null) FieldNote.Problem else FieldNote.Plain,
        trailingIcon = if (isNewAccount) R.drawable.icon_mail else R.drawable.icon_user_round,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = imeAction),
    )
}

@Composable
fun GoogleButton(onClick: () -> Unit, enabled: Boolean, text: String = "Continue with Google") {
    SecondaryButton(
        text,
        onClick = onClick,
        enabled = enabled,
        leading = { Text("G", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
    )
}

@Composable
fun ErrorMessage(message: String?) {
    if (message.isNullOrEmpty()) return
    Text(message, color = Inkless.colors.danger, style = MaterialTheme.typography.bodyMedium)
}

@Composable
fun OrDivider() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        HorizontalDivider(Modifier.weight(1f), color = Inkless.colors.hairline)
        Spacer(Modifier.width(12.dp))
        Text("or", style = MaterialTheme.typography.bodySmall, color = Inkless.colors.muted)
        Spacer(Modifier.width(12.dp))
        HorizontalDivider(Modifier.weight(1f), color = Inkless.colors.hairline)
    }
}
