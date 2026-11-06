package com.mehul.inkless.ui.data

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mehul.inkless.R
import java.time.LocalDate

/** The app lock, and getting your data out (and back in) as files. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataScreen(onBack: () -> Unit, viewModel: DataViewModel = hiltViewModel()) {
    val activity = LocalActivity.current as FragmentActivity
    val lockOn by viewModel.appLock.enabled.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val needsPassword by viewModel.needsPassword.collectAsStateWithLifecycle()
    var choosingPassword by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val today = LocalDate.now()

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); viewModel.messageShown() }
    }

    // Android's own "save as" and "open" pickers: the user chooses where, no storage permission needed.
    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { viewModel.export(ExportKind.CSV, it) }
    }
    val saveJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { viewModel.export(ExportKind.JSON, it) }
    }
    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let { viewModel.export(ExportKind.BACKUP, it) }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::restore)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy & your data") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())

            Title("App lock")
            val available = viewModel.lockAvailable()
            ListItem(
                modifier = Modifier.clickable(enabled = available || lockOn) {
                    if (lockOn) {
                        viewModel.appLock.setEnabled(false)
                    } else {
                        // Proves the unlock works before it's needed.
                        viewModel.appLock.prompt(activity, "Turn on app lock") { ok -> if (ok) viewModel.appLock.setEnabled(true) }
                    }
                },
                headlineContent = { Text("Lock Inkless") },
                supportingContent = {
                    Text(
                        if (available || lockOn) {
                            "Ask for your fingerprint, face or screen lock when Inkless opens, and hide it from the recent apps preview."
                        } else {
                            "Set a screen lock on your phone first (Settings → Security)."
                        },
                    )
                },
                trailingContent = { Switch(checked = lockOn, onCheckedChange = null, enabled = available || lockOn) },
            )

            Title("Export")
            Action("Payments as a spreadsheet", "A CSV file for Excel or Google Sheets: date, amount, category, place, note, items.", !busy) {
                saveCsv.launch("inkless-payments-$today.csv")
            }
            Action("Everything as JSON", "Payments, categories, merchants, rules, budget, friends and splits, readable by other apps. Not locked: keep it private.", !busy) {
                saveJson.launch("inkless-$today.json")
            }

            Title("Backup file")
            Text(
                "A copy of everything, locked with a password, for keeping offline or moving to a new phone without the cloud. " +
                    "Restoring adds what's missing and keeps whichever copy of a payment was edited last.",
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Action("Save a backup file", "Asks for a password first.", !busy) { choosingPassword = true }
            Action(
                "Restore from a backup file",
                if (viewModel.isDemo) "Log in to restore; the demo's data is made up." else "Pick a .inkless (or exported JSON) file.",
                !busy && !viewModel.isDemo,
            ) { openBackup.launch(arrayOf("*/*")) }

            Title("What leaves this phone")
            Text(
                "Only Firebase: signing in, the cloud backup if it's on, and splits you share with friends. " +
                    "Never the text of an SMS or notification, and never more than the last 4 digits of an account number. " +
                    "Files you export go only where you save them.",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (choosingPassword) {
        PasswordDialog(
            title = "Lock the backup",
            confirm = true,
            onDone = { password ->
                choosingPassword = false
                viewModel.setBackupPassword(password)
                saveBackup.launch("inkless-$today.inkless")
            },
            onDismiss = { choosingPassword = false },
        )
    }
    if (needsPassword != null) {
        PasswordDialog(
            title = "Backup password",
            confirm = false,
            onDone = viewModel::restoreWithPassword,
            onDismiss = viewModel::passwordDismissed,
        )
    }
}

@Composable
private fun PasswordDialog(title: String, confirm: Boolean, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    val long = password.length >= MIN_PASSWORD
    val ok = if (confirm) long && password == again else password.isNotEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (confirm) {
                    Text(
                        "At least $MIN_PASSWORD characters. There's no way to open the file without it, so write it down.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                PasswordField(password, { password = it }, "Password")
                if (confirm) PasswordField(again, { again = it }, "Same password again")
            }
        },
        confirmButton = { TextButton(onClick = { onDone(password) }, enabled = ok) { Text(if (confirm) "Choose where to save" else "Restore") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(128)) },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Title(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun Action(title: String, supporting: String, enabled: Boolean, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        headlineContent = { Text(title, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant) },
        supportingContent = { Text(supporting) },
    )
}

private const val MIN_PASSWORD = 8
