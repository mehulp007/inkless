package com.mehul.inkless.ui.data

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mehul.inkless.R
import com.mehul.inkless.ui.components.InklessBottomSheet
import com.mehul.inkless.ui.components.InklessCard
import com.mehul.inkless.ui.components.InklessTopBar
import com.mehul.inkless.ui.components.NavRow
import com.mehul.inkless.ui.components.PageHeadline
import com.mehul.inkless.ui.components.PasswordField
import com.mehul.inkless.ui.components.PrimaryButton
import com.mehul.inkless.ui.components.PrivacyNote
import com.mehul.inkless.ui.components.SectionHeader
import com.mehul.inkless.ui.components.TintCard
import com.mehul.inkless.ui.components.ToggleRow
import com.mehul.inkless.ui.theme.Inkless
import com.mehul.inkless.ui.theme.Spacing
import java.time.LocalDate

/** What the Privacy & your data page shows. */
data class DataState(
    val lockOn: Boolean = false,
    /** False when the phone has no screen lock, so there's nothing to unlock with. */
    val lockAvailable: Boolean = true,
    val busy: Boolean = false,
    val isDemo: Boolean = false,
)

/** The app lock, and getting your data out (and back in) as files. */
@Composable
fun DataScreen(onBack: () -> Unit, viewModel: DataViewModel = hiltViewModel()) {
    val activity = LocalActivity.current as FragmentActivity
    val lockOn by viewModel.appLock.enabled.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val needsPassword by viewModel.needsPassword.collectAsStateWithLifecycle()
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

    DataContent(
        state = DataState(lockOn = lockOn, lockAvailable = viewModel.lockAvailable(), busy = busy, isDemo = viewModel.isDemo),
        onBack = onBack,
        onLock = { on ->
            if (!on) {
                viewModel.appLock.setEnabled(false)
            } else {
                // Proves the unlock works before it's needed.
                viewModel.appLock.prompt(activity, "Turn on app lock") { ok -> if (ok) viewModel.appLock.setEnabled(true) }
            }
        },
        onExportCsv = { saveCsv.launch("inkless-payments-$today.csv") },
        onExportJson = { saveJson.launch("inkless-$today.json") },
        onSaveBackup = { password ->
            viewModel.setBackupPassword(password)
            saveBackup.launch("inkless-$today.inkless")
        },
        onRestore = { openBackup.launch(arrayOf("*/*")) },
        askingRestorePassword = needsPassword != null,
        onRestorePassword = viewModel::restoreWithPassword,
        onRestorePasswordDismissed = viewModel::passwordDismissed,
        snackbar = snackbar,
    )
}

@Composable
fun DataContent(
    state: DataState,
    onBack: () -> Unit,
    onLock: (Boolean) -> Unit,
    onExportCsv: () -> Unit,
    onExportJson: () -> Unit,
    onSaveBackup: (password: String) -> Unit,
    onRestore: () -> Unit,
    askingRestorePassword: Boolean = false,
    onRestorePassword: (String) -> Unit = {},
    onRestorePasswordDismissed: () -> Unit = {},
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    val colors = Inkless.colors
    var choosingPassword by rememberSaveable { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize(), color = colors.background) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                InklessTopBar("Privacy & your data", onBack = onBack)
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = colors.primary, trackColor = colors.tintStrong)
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.s, bottom = Spacing.xl),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xl),
                ) {
                    PageHeadline("Your data, your call.", lead = "Lock the app, take everything with you, or keep a copy offline.")
                    TintCard {
                        PrivacyNote(
                            "What leaves this phone",
                            body = "Only Firebase: signing in, the cloud backup if it's on, and splits you share with friends. " +
                                "Never the text of an SMS or notification, and never more than the last 4 digits of an account number. " +
                                "Files you export go only where you save them.",
                        )
                    }

                    InklessCard(padding = PaddingValues(horizontal = Spacing.l, vertical = Spacing.xs)) {
                        val canToggle = state.lockAvailable || state.lockOn
                        ToggleRow(
                            title = "App lock",
                            checked = state.lockOn,
                            onCheckedChange = onLock,
                            subtitle = if (canToggle) {
                                "Ask for your fingerprint, face or screen lock when Inkless opens, and hide it from the recent apps preview."
                            } else {
                                "Set a screen lock on your phone first (Settings → Security)."
                            },
                            icon = R.drawable.icon_fingerprint,
                            enabled = canToggle,
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                        SectionHeader("Export")
                        InklessCard(padding = PaddingValues(vertical = Spacing.xs)) {
                            NavRow(
                                "Payments as a spreadsheet",
                                subtitle = "A CSV file for Excel or Google Sheets: date, amount, category, place, note, items.",
                                icon = R.drawable.icon_download,
                                enabled = !state.busy,
                                onClick = onExportCsv,
                            )
                            HorizontalDivider(Modifier.padding(horizontal = Spacing.l), color = colors.hairline)
                            NavRow(
                                "Everything as JSON",
                                subtitle = "Payments, categories, merchants, rules, budget, friends and splits, readable by other apps. Not locked: keep it private.",
                                icon = R.drawable.icon_download,
                                enabled = !state.busy,
                                onClick = onExportJson,
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                        SectionHeader("Backup file")
                        Text(
                            "A copy of everything, locked with a password, for keeping offline or moving to a new phone without the cloud. " +
                                "Restoring adds what's missing and keeps whichever copy of a payment was edited last.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.muted,
                        )
                        InklessCard(padding = PaddingValues(vertical = Spacing.xs)) {
                            NavRow(
                                "Save a backup file",
                                subtitle = "Asks for a password first.",
                                icon = R.drawable.icon_lock,
                                enabled = !state.busy,
                            ) { choosingPassword = true }
                            HorizontalDivider(Modifier.padding(horizontal = Spacing.l), color = colors.hairline)
                            NavRow(
                                "Restore from a backup file",
                                subtitle = if (state.isDemo) "Log in to restore; the demo's data is made up." else "Pick a .inkless (or exported JSON) file.",
                                icon = R.drawable.icon_upload,
                                enabled = !state.busy && !state.isDemo,
                                onClick = onRestore,
                            )
                        }
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        }
    }

    if (choosingPassword) {
        PasswordSheet(
            title = "Lock the backup",
            confirm = true,
            onDone = { password ->
                choosingPassword = false
                onSaveBackup(password)
            },
            onDismiss = { choosingPassword = false },
        )
    }
    if (askingRestorePassword) {
        PasswordSheet(
            title = "Backup password",
            confirm = false,
            onDone = onRestorePassword,
            onDismiss = onRestorePasswordDismissed,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PasswordSheet(title: String, confirm: Boolean, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    val long = password.length >= MIN_PASSWORD
    val ok = if (confirm) long && password == again else password.isNotEmpty()
    InklessBottomSheet(onDismiss) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = Inkless.colors.ink)
        Text(
            if (confirm) {
                "At least $MIN_PASSWORD characters. There's no way to open the file without it, so write it down."
            } else {
                "The password this file was saved with."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = Inkless.colors.muted,
        )
        PasswordField(password, { password = it }, label = "Password")
        if (confirm) {
            PasswordField(
                again,
                { again = it },
                label = "Same password again",
                error = if (again.isNotEmpty() && again != password) "The two don't match" else null,
            )
        }
        PrimaryButton(if (confirm) "Choose where to save" else "Restore", onClick = { onDone(password) }, enabled = ok)
    }
}

private const val MIN_PASSWORD = 8
