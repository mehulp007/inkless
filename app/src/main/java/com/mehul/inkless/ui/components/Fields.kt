package com.mehul.inkless.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.mehul.inkless.R
import com.mehul.inkless.ui.theme.Inkless
import com.mehul.inkless.ui.theme.Spacing

/** What the line under a field is saying. */
enum class FieldNote { Plain, Good, Problem }

/**
 * A text field the Figma way: the label sits above the box, the box is the raised surface with a
 * hairline border (green while typing), and a short note can sit underneath.
 */
@Composable
fun InklessTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    note: String? = null,
    noteKind: FieldNote = FieldNote.Plain,
    @DrawableRes trailingIcon: Int? = null,
    trailingDescription: String? = null,
    onTrailingClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    prefix: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    money: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val colors = Inkless.colors
    val noteColor = when (noteKind) {
        FieldNote.Plain -> colors.muted
        FieldNote.Good -> colors.primary
        FieldNote.Problem -> colors.danger
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        if (label != null) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = colors.muted)
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            singleLine = singleLine,
            textStyle = if (money) Inkless.type.moneyMedium else MaterialTheme.typography.bodyLarge,
            placeholder = placeholder?.let { { Text(it, color = colors.muted) } },
            isError = noteKind == FieldNote.Problem,
            // A money prefix is as big as the amount, so "₹12000" reads as one number.
            prefix = prefix?.let { { Text(it, style = if (money) Inkless.type.moneyMedium else MaterialTheme.typography.bodyLarge, color = if (money) colors.ink else colors.muted) } },
            trailingIcon = trailingContent ?: trailingIcon?.let { icon ->
                {
                    val glyph = @Composable {
                        Icon(painterResource(icon), contentDescription = trailingDescription, tint = colors.muted)
                    }
                    if (onTrailingClick != null) IconButton(onClick = onTrailingClick) { glyph() } else glyph()
                }
            },
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
                disabledContainerColor = colors.surface,
                errorContainerColor = colors.surface,
                focusedBorderColor = colors.primary,
                unfocusedBorderColor = colors.hairline,
                disabledBorderColor = colors.hairline,
                errorBorderColor = colors.danger,
                focusedTextColor = colors.ink,
                unfocusedTextColor = colors.ink,
                cursorColor = colors.primary,
            ),
        )
        if (note != null) {
            Text(note, style = MaterialTheme.typography.bodySmall, color = noteColor)
        }
    }
}

/** A password field with a show/hide eye; used for logging in, signing up and backup files. */
@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true,
    label: String = "Password",
    isNewPassword: Boolean = false,
    error: String? = null,
    supporting: String? = null,
    imeAction: ImeAction = ImeAction.Done,
    onDone: () -> Unit = {},
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    InklessTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.semantics {
            contentType = if (isNewPassword) ContentType.NewPassword else ContentType.Password
        },
        label = label,
        note = error ?: supporting,
        noteKind = if (error != null) FieldNote.Problem else FieldNote.Plain,
        trailingIcon = if (visible) R.drawable.icon_eye else R.drawable.icon_eye_off,
        trailingDescription = if (visible) "Hide password" else "Show password",
        onTrailingClick = { visible = !visible },
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
    )
}

/**
 * Looks like a field but opens a choice: "Groceries & Essentials ∨". [label] sits above it like
 * every other field's; [note] under it.
 */
@Composable
fun ChoiceField(
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    note: String? = null,
    @DrawableRes icon: Int = R.drawable.icon_chevron_down,
    enabled: Boolean = true,
) {
    val colors = Inkless.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        if (label != null) Text(label, style = MaterialTheme.typography.labelMedium, color = colors.muted)
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = colors.surface,
            border = BorderStroke(1.dp, colors.hairline),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick),
        ) {
            Row(Modifier.padding(Spacing.l), verticalAlignment = Alignment.CenterVertically) {
                Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = if (enabled) colors.ink else colors.muted)
                if (enabled) Icon(painterResource(icon), contentDescription = null, tint = colors.muted, modifier = Modifier.size(20.dp))
            }
        }
        if (note != null) Text(note, style = MaterialTheme.typography.bodySmall, color = colors.muted)
    }
}
