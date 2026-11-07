package com.mehul.inkless.ui.friends

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.mehul.inkless.data.db.entity.FriendEntity
import com.mehul.inkless.data.db.entity.IouEntity
import com.mehul.inkless.parsing.Amounts
import com.mehul.inkless.ui.components.ChipTone
import com.mehul.inkless.ui.components.FieldNote
import com.mehul.inkless.ui.components.InklessBottomSheet
import com.mehul.inkless.ui.components.InklessTextField
import com.mehul.inkless.ui.components.PillChoice
import com.mehul.inkless.ui.components.PrimaryButton
import com.mehul.inkless.ui.components.TextLink
import com.mehul.inkless.ui.format.Money
import com.mehul.inkless.ui.theme.Inkless

/** Adding or editing a friend: a name, their UPI IDs, and their Inkless username if they have one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendSheet(
    friend: FriendEntity?,
    busy: Boolean,
    error: String?,
    onSave: (name: String, vpas: List<String>, username: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(friend?.name.orEmpty()) }
    var vpas by rememberSaveable { mutableStateOf(friend?.vpas?.joinToString(", ").orEmpty()) }
    var username by rememberSaveable { mutableStateOf(friend?.linkedUsername.orEmpty()) }

    InklessBottomSheet(onDismiss = onDismiss) {
        Text(if (friend == null) "Add a friend" else "Edit ${friend.name}", style = MaterialTheme.typography.displaySmall, color = Inkless.colors.ink)
        InklessTextField(
            value = name,
            onValueChange = { name = it.take(40) },
            label = "Name",
            placeholder = "Rohan Mehta",
            enabled = !busy,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        )
        InklessTextField(
            value = vpas,
            onValueChange = { vpas = it.take(120) },
            label = "UPI IDs · optional",
            placeholder = "rohan@okaxis",
            note = "So money from them is recognised. Separate several with commas.",
            enabled = !busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        InklessTextField(
            value = username,
            onValueChange = { username = it.take(21) },
            label = "Their Inkless username · optional",
            prefix = "@",
            note = error ?: "Splits with them then show up in their app too.",
            noteKind = if (error != null) FieldNote.Problem else FieldNote.Plain,
            enabled = !busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
        )
        PrimaryButton(
            if (friend == null) "Add friend" else "Save",
            onClick = {
                onSave(name.trim(), vpas.split(',').map { it.trim() }.filter { it.isNotEmpty() }, username.trim().ifEmpty { null })
            },
            enabled = name.isNotBlank(),
            busy = busy,
        )
    }
}

/**
 * Money lent to or borrowed from a friend outside any payment Inkless saw. With [iou] it edits
 * that entry, and offers to mark it paid back or delete it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IouSheet(
    friendName: String,
    iou: IouEntity?,
    onSave: (amountPaise: Long, theyOwe: Boolean, note: String?) -> Unit,
    onSettle: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val first = friendName.substringBefore(' ')
    var amount by rememberSaveable { mutableStateOf(iou?.amountPaise?.let(::plain).orEmpty()) }
    var theyOwe by rememberSaveable { mutableStateOf(iou?.theyOwe ?: true) }
    var note by rememberSaveable { mutableStateOf(iou?.note.orEmpty()) }
    val paise = Amounts.toPaise(amount)?.takeIf { it > 0 }

    InklessBottomSheet(onDismiss = onDismiss) {
        Text(if (iou == null) "Lent or borrowed" else "Edit this entry", style = MaterialTheme.typography.displaySmall, color = Inkless.colors.ink)
        PillChoice(
            labels = listOf("I lent $first", "$first lent me"),
            selected = if (theyOwe) 0 else 1,
            onSelect = { theyOwe = it == 0 },
        )
        InklessTextField(
            value = amount,
            onValueChange = { new -> amount = new.filter { it.isDigit() || it == '.' }.take(10) },
            label = "Amount",
            prefix = "₹",
            money = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        InklessTextField(
            value = note,
            onValueChange = { note = it.take(60) },
            label = "What for? · optional",
            placeholder = "Auto to the station",
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        PrimaryButton(if (iou == null) "Save" else "Save changes", onClick = { paise?.let { onSave(it, theyOwe, note) } }, enabled = paise != null)
        if (iou != null) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (!iou.settled) TextLink("Mark paid back", onClick = onSettle)
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextLink("Delete this entry", onClick = onDelete, color = Inkless.colors.danger)
            }
        }
    }
}

/** How a friend's balance reads, and the tone to show it in. */
data class BalanceWords(val amount: String, val words: String, val tone: ChipTone)

/** +15000 -> "₹150", "owes you"; −8000 -> "₹80", "you owe"; 0 -> "", "all square". */
fun balanceWords(netPaise: Long): BalanceWords = when {
    netPaise > 0 -> BalanceWords(Money.format(netPaise), "owes you", ChipTone.Good)
    netPaise < 0 -> BalanceWords(Money.format(-netPaise), "you owe", ChipTone.Check)
    else -> BalanceWords("", "all square", ChipTone.Neutral)
}

private fun plain(paise: Long) = if (paise % 100 == 0L) "${paise / 100}" else "%d.%02d".format(paise / 100, paise % 100)
