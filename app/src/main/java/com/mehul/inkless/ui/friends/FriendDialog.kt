package com.mehul.inkless.ui.friends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mehul.inkless.data.db.entity.FriendEntity

/** Adding or editing a friend: a name, their UPI IDs, and their Inkless username if they have one. */
@Composable
fun FriendDialog(
    friend: FriendEntity?,
    busy: Boolean,
    error: String?,
    onSave: (name: String, vpas: List<String>, username: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(friend?.name.orEmpty()) }
    var vpas by rememberSaveable { mutableStateOf(friend?.vpas?.joinToString(", ").orEmpty()) }
    var username by rememberSaveable { mutableStateOf(friend?.linkedUsername.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (friend == null) "Add a friend" else "Edit ${friend.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it.take(40) }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
                OutlinedTextField(
                    vpas,
                    { vpas = it.take(120) },
                    Modifier.fillMaxWidth(),
                    label = { Text("UPI IDs (optional)") },
                    supportingText = { Text("So a payment from them is recognised. Separate several with commas.") },
                    singleLine = true,
                )
                OutlinedTextField(
                    username,
                    { username = it.take(21) },
                    Modifier.fillMaxWidth(),
                    label = { Text("Their Inkless username (optional)") },
                    prefix = { Text("@") },
                    supportingText = { Text("Splits with them then show up in their app too.") },
                    singleLine = true,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, vpas.split(',', ' ').filter { it.isNotBlank() }, username.ifBlank { null }) },
                enabled = name.isNotBlank() && !busy,
            ) { Text(if (busy) "Saving…" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
