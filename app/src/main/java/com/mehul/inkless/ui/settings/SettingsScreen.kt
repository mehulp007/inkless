package com.mehul.inkless.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mehul.inkless.R
import com.mehul.inkless.auth.AuthValidation
import com.mehul.inkless.breakdown.BreakdownPrefs
import com.mehul.inkless.sync.BackupStatus
import com.mehul.inkless.ui.auth.ErrorMessage
import com.mehul.inkless.ui.auth.FormStatus
import com.mehul.inkless.ui.auth.GoogleButton
import com.mehul.inkless.ui.components.ConfirmDialog
import com.mehul.inkless.ui.components.Footnote
import com.mehul.inkless.ui.components.IconTile
import com.mehul.inkless.ui.components.InitialTile
import com.mehul.inkless.ui.components.InklessBottomSheet
import com.mehul.inkless.ui.components.InklessCard
import com.mehul.inkless.ui.components.InklessTopBar
import com.mehul.inkless.ui.components.NavRow
import com.mehul.inkless.ui.components.PasswordField
import com.mehul.inkless.ui.components.PillChoice
import com.mehul.inkless.ui.components.SectionHeader
import com.mehul.inkless.ui.components.TextLink
import com.mehul.inkless.ui.components.WarmCard
import com.mehul.inkless.ui.format.Money
import com.mehul.inkless.ui.health.CaptureHealth
import com.mehul.inkless.ui.theme.Appearance
import com.mehul.inkless.ui.theme.Inkless
import com.mehul.inkless.ui.theme.Spacing
import java.time.ZoneId

/** Everything the Settings page shows. */
data class SettingsState(
    val mode: SettingsMode,
    val backup: BackupStatus = BackupStatus(),
    val ledger: LedgerSummary = LedgerSummary(),
    /** Null until the phone has been asked. */
    val health: CaptureHealth? = null,
    val unreadCount: Int = 0,
    val askAbovePaise: Long? = BreakdownPrefs.DEFAULT_PAISE,
    val appearance: Appearance = Appearance.SYSTEM,
    val version: String = "",
    val busy: Boolean = false,
    val nowMillis: Long = 0,
)

/** What Settings can ask for. Each defaults to doing nothing, so a test passes only what it checks. */
class SettingsActions(
    val onBack: () -> Unit = {},
    val onOpenBudget: () -> Unit = {},
    val onOpenRules: () -> Unit = {},
    val onOpenMyAccounts: () -> Unit = {},
    val onOpenHealth: () -> Unit = {},
    val onOpenUnread: () -> Unit = {},
    val onOpenData: () -> Unit = {},
    val onAppearance: (Appearance) -> Unit = {},
    val onAskAbove: (Long?) -> Unit = {},
    val onResendVerification: () -> Unit = {},
    val onLinkGoogle: () -> Unit = {},
    val onBackUpNow: () -> Unit = {},
    val onTurnOnBackup: () -> Unit = {},
    val onTurnOffBackup: () -> Unit = {},
    val onLogOut: () -> Unit = {},
    val onBackUpAndLogOut: () -> Unit = {},
    val onExitDemo: () -> Unit = {},
    val onChangePassword: (current: String, new: String) -> Unit = { _, _ -> },
    val onDeleteWithPassword: (String) -> Unit = {},
    val onDeleteWithGoogle: () -> Unit = {},
    val onDialogClosed: () -> Unit = {},
)

private enum class SettingsDialog { CHANGE_PASSWORD, DELETE_ACCOUNT, LOG_OUT, TURN_OFF_BACKUP, ASK_ABOVE }

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenUnread: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenMyAccounts: () -> Unit,
    onOpenHealth: () -> Unit,
    onOpenBudget: () -> Unit,
    onOpenData: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    // Permissions can change in system settings while Inkless is in the background.
    var resumed by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumed++
        viewModel.recheckPermission()
        onPauseOrDispose {}
    }
    val health = remember(resumed) { CaptureHealth.read(context) }
    val capture by viewModel.capture.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val dialogStatus by viewModel.dialogStatus.collectAsStateWithLifecycle()
    val backup by viewModel.backup.collectAsStateWithLifecycle()
    val askAbove by viewModel.askAbovePaise.collectAsStateWithLifecycle()
    val appearance by viewModel.appearance.collectAsStateWithLifecycle()
    val ledger by viewModel.ledger.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val version = remember {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    SettingsContent(
        state = SettingsState(
            mode = mode,
            backup = backup,
            ledger = ledger,
            health = health,
            unreadCount = capture.unreadCount,
            askAbovePaise = askAbove,
            appearance = appearance,
            version = version,
            busy = busy,
            nowMillis = remember(backup) { System.currentTimeMillis() },
        ),
        actions = SettingsActions(
            onBack = onBack,
            onOpenBudget = onOpenBudget,
            onOpenRules = onOpenRules,
            onOpenMyAccounts = onOpenMyAccounts,
            onOpenHealth = onOpenHealth,
            onOpenUnread = onOpenUnread,
            onOpenData = onOpenData,
            onAppearance = viewModel::setAppearance,
            onAskAbove = viewModel::setAskAbove,
            onResendVerification = viewModel::resendVerification,
            onLinkGoogle = { viewModel.linkGoogle(context) },
            onBackUpNow = viewModel::backUpNow,
            onTurnOnBackup = viewModel::turnOnBackup,
            onTurnOffBackup = viewModel::turnOffBackup,
            onLogOut = viewModel::logOut,
            onBackUpAndLogOut = viewModel::backUpAndLogOut,
            onExitDemo = viewModel::exitDemo,
            onChangePassword = viewModel::changePassword,
            onDeleteWithPassword = viewModel::deleteWithPassword,
            onDeleteWithGoogle = { viewModel.deleteWithGoogle(context) },
            onDialogClosed = viewModel::dialogClosed,
        ),
        dialogStatus = dialogStatus,
        snackbar = snackbar,
    )
}

@Composable
fun SettingsContent(
    state: SettingsState,
    actions: SettingsActions,
    dialogStatus: FormStatus = FormStatus(),
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    val colors = Inkless.colors
    var dialog by rememberSaveable { mutableStateOf<SettingsDialog?>(null) }
    val closeDialog = {
        dialog = null
        actions.onDialogClosed()
    }
    // Password changed: close the dialog and say so. (Deleting the account swaps the screen out instead.)
    LaunchedEffect(dialogStatus.done) {
        if (dialogStatus.done && dialog == SettingsDialog.CHANGE_PASSWORD) {
            closeDialog()
            snackbar.showSnackbar("Password changed")
        }
    }
    val account = state.mode as? SettingsMode.Account
    val demo = state.mode == SettingsMode.Demo

    Surface(Modifier.fillMaxSize(), color = colors.background) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                InklessTopBar("Settings", onBack = actions.onBack)
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.s, bottom = Spacing.xl),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xl),
                ) {
                    if (account != null || demo) Profile(account, demo)
                    if (account != null && !account.emailVerified) {
                        WarmCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("Verify your email when you can", style = MaterialTheme.typography.titleSmall)
                                    Text("You can keep using Inkless.", style = MaterialTheme.typography.bodySmall)
                                }
                                TextLink("Resend", actions.onResendVerification, enabled = !state.busy, color = colors.onWarm)
                            }
                        }
                    }
                    if (account != null) {
                        BackupCard(
                            backup = state.backup,
                            nowMillis = state.nowMillis,
                            busy = state.busy,
                            onToggle = { if (state.backup.on) dialog = SettingsDialog.TURN_OFF_BACKUP else actions.onTurnOnBackup() },
                            onBackUpNow = actions.onBackUpNow,
                        )
                    }
                    if (account != null || demo) {
                        Section("Your ledger") {
                            NavRow("Allowance & budgets", subtitle = state.ledger.allowance, icon = R.drawable.icon_wallet, onClick = actions.onOpenBudget)
                            Divider()
                            NavRow("Categories & rules", subtitle = state.ledger.categories, icon = R.drawable.icon_tags, onClick = actions.onOpenRules)
                            Divider()
                            NavRow("My accounts", subtitle = state.ledger.accounts, icon = R.drawable.icon_landmark, onClick = actions.onOpenMyAccounts)
                            Divider()
                            NavRow(
                                "Capture health",
                                subtitle = state.health?.settingsLine ?: "SMS, notifications, battery",
                                icon = R.drawable.icon_activity,
                                onClick = actions.onOpenHealth,
                            )
                            Divider()
                            NavRow(
                                "Couldn't read these",
                                subtitle = when (state.unreadCount) {
                                    0 -> "Every bank message was understood"
                                    1 -> "1 bank message to look at"
                                    else -> "${state.unreadCount} bank messages to look at"
                                },
                                icon = R.drawable.icon_message_question,
                                onClick = actions.onOpenUnread,
                            )
                            if (account != null) {
                                Divider()
                                NavRow(
                                    "Ask to break down",
                                    subtitle = SettingsText.askAbove(state.askAbovePaise),
                                    icon = R.drawable.icon_list_tree,
                                    onClick = { dialog = SettingsDialog.ASK_ABOVE },
                                )
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                            SectionHeader("Appearance")
                            AppearanceChoice(state.appearance, actions.onAppearance)
                        }
                    }
                    Section("Account & data") {
                        NavRow(
                            "Privacy & your data",
                            subtitle = "App lock, export as CSV or JSON, backup file",
                            icon = R.drawable.icon_shield_check,
                            onClick = actions.onOpenData,
                        )
                        if (account != null) {
                            if (!account.hasGoogle) {
                                Divider()
                                NavRow(
                                    "Link Google account",
                                    subtitle = "Then you can log in with either",
                                    icon = R.drawable.icon_link,
                                    enabled = !state.busy,
                                    onClick = actions.onLinkGoogle,
                                )
                            }
                            if (account.hasPassword) {
                                Divider()
                                NavRow("Change password", icon = R.drawable.icon_key_round, enabled = !state.busy) {
                                    dialog = SettingsDialog.CHANGE_PASSWORD
                                }
                            }
                            Divider()
                            NavRow("Log out", icon = R.drawable.icon_log_out, enabled = !state.busy, showChevron = false) {
                                dialog = SettingsDialog.LOG_OUT
                            }
                        }
                        if (demo) {
                            Divider()
                            NavRow("Exit demo", subtitle = "Back to the welcome screen", icon = R.drawable.icon_log_out, showChevron = false, onClick = actions.onExitDemo)
                        }
                    }
                    if (account != null) {
                        TextLink("Delete account", { dialog = SettingsDialog.DELETE_ACCOUNT }, enabled = !state.busy, color = colors.danger)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Footnote("Inkless · v${state.version}" + if (demo) " demo" else "")
                        Footnote("The ledger that writes itself.")
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        }
    }

    when (dialog) {
        SettingsDialog.LOG_OUT -> {
            val unsent = BackupText.changes(state.backup.pending)?.takeIf { state.backup.on }
            if (unsent == null) {
                ConfirmDialog(
                    title = "Log out?",
                    body = "Your data stays on this phone and comes back when you log in again.",
                    confirmText = "Log out",
                    onConfirm = { closeDialog(); actions.onLogOut() },
                    onDismiss = closeDialog,
                )
            } else {
                val one = state.backup.pending == 1
                AlertDialog(
                    onDismissRequest = closeDialog,
                    containerColor = colors.surface,
                    title = { Text("Back up before logging out?", color = colors.ink) },
                    text = {
                        Text(
                            "$unsent on this phone ${if (one) "isn't" else "aren't"} backed up yet. " +
                                "${if (one) "It stays" else "They stay"} here, but won't reach your " +
                                "other phones until you log in here again.",
                            color = colors.muted,
                        )
                    },
                    confirmButton = { TextLink("Back up and log out", { closeDialog(); actions.onBackUpAndLogOut() }) },
                    dismissButton = {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
                            TextLink("Log out anyway", { closeDialog(); actions.onLogOut() }, color = colors.muted)
                            TextLink("Cancel", closeDialog, color = colors.muted)
                        }
                    },
                )
            }
        }
        SettingsDialog.ASK_ABOVE -> AskAboveSheet(
            selected = state.askAbovePaise,
            onSelect = {
                actions.onAskAbove(it)
                closeDialog()
            },
            onDismiss = closeDialog,
        )
        SettingsDialog.TURN_OFF_BACKUP -> ConfirmDialog(
            title = "Turn off cloud backup?",
            body = "Backup stops on every phone you use Inkless on, and your payments, categories, merchants, " +
                "rules and accounts are deleted from the cloud. Everything stays on this phone, but a new " +
                "phone won't get it back.",
            confirmText = "Turn off and delete",
            onConfirm = { closeDialog(); actions.onTurnOffBackup() },
            onDismiss = closeDialog,
            destructive = true,
        )
        SettingsDialog.CHANGE_PASSWORD -> ChangePasswordDialog(
            status = dialogStatus,
            onConfirm = actions.onChangePassword,
            onDismiss = closeDialog,
        )
        SettingsDialog.DELETE_ACCOUNT -> if (account != null) {
            DeleteAccountDialog(
                usesPassword = account.hasPassword,
                status = dialogStatus,
                onConfirmPassword = actions.onDeleteWithPassword,
                onConfirmGoogle = actions.onDeleteWithGoogle,
                onDismiss = closeDialog,
            )
        }
        null -> Unit
    }
}

@Composable
private fun Profile(account: SettingsMode.Account?, demo: Boolean) {
    val colors = Inkless.colors
    val name = account?.username ?: if (demo) "Demo ledger" else "Your account"
    val line = when {
        demo -> "Made-up sample data · Demo account"
        account?.email != null -> account.email
        else -> "Signed in"
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
        InitialTile(name, size = 56.dp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, style = MaterialTheme.typography.titleLarge, color = colors.ink)
            Text(line, style = MaterialTheme.typography.bodyMedium, color = colors.muted)
        }
    }
}

@Composable
private fun BackupCard(backup: BackupStatus, nowMillis: Long, busy: Boolean, onToggle: () -> Unit, onBackUpNow: () -> Unit) {
    val colors = Inkless.colors
    InklessCard {
        Row(
            Modifier
                .fillMaxWidth()
                .selectable(selected = backup.on, enabled = !busy, role = Role.Switch, onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            IconTile(R.drawable.icon_cloud_check)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Cloud backup", style = MaterialTheme.typography.titleMedium, color = colors.ink)
                Text(BackupText.describe(backup, nowMillis, ZoneId.systemDefault()), style = MaterialTheme.typography.bodySmall, color = colors.muted)
            }
            Switch(
                checked = backup.on,
                onCheckedChange = null,
                enabled = !busy,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = colors.primary,
                    checkedThumbColor = colors.onPrimary,
                    checkedBorderColor = colors.primary,
                    uncheckedTrackColor = colors.tintStrong,
                    uncheckedThumbColor = colors.surface,
                    uncheckedBorderColor = colors.tintStrong,
                ),
            )
        }
        Text(
            "Only parsed ledger data is backed up. Raw SMS and notification text stay on your phone.",
            Modifier.padding(top = Spacing.m),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.ink,
        )
        if (backup.on) {
            TextLink("Back up now", onBackUpNow, Modifier.padding(top = Spacing.xs), enabled = !busy && !backup.running)
        }
        HorizontalDivider(Modifier.padding(vertical = Spacing.m), color = colors.hairline)
        Text(
            "Turning this off asks for confirmation, then deletes your cloud copy. Your phone keeps its ledger.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.muted,
        )
    }
}

/** A titled card of rows, the way Settings groups its links. */
@Composable
private fun Section(title: String, rows: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        SectionHeader(title)
        InklessCard(padding = PaddingValues(vertical = Spacing.xs)) { rows() }
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(Modifier.padding(start = 54.dp, end = Spacing.l), color = Inkless.colors.hairline)
}

/** Light · Dark · System on a green track; the picked one is raised with a tick. */
@Composable
private fun AppearanceChoice(selected: Appearance, onSelect: (Appearance) -> Unit) {
    val colors = Inkless.colors
    Surface(shape = CircleShape, color = colors.tint) {
        Row(Modifier.padding(4.dp).selectableGroup()) {
            Appearance.entries.forEach { mode ->
                val isSelected = mode == selected
                Surface(
                    shape = CircleShape,
                    color = if (isSelected) colors.surface else colors.tint,
                    contentColor = if (isSelected) colors.primary else colors.muted,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .selectable(isSelected, role = Role.RadioButton, onClick = { onSelect(mode) }),
                ) {
                    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        if (isSelected) {
                            Icon(painterResource(R.drawable.icon_check), contentDescription = null, modifier = Modifier.padding(end = 6.dp).size(16.dp))
                        }
                        Text(
                            mode.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AskAboveSheet(selected: Long?, onSelect: (Long?) -> Unit, onDismiss: () -> Unit) {
    val choices: List<Long?> = BreakdownPrefs.CHOICES + listOf<Long?>(null)
    InklessBottomSheet(onDismiss) {
        Text("Ask to break down", style = MaterialTheme.typography.headlineSmall, color = Inkless.colors.ink)
        Text(
            "After a payment over this amount, Inkless asks whether it was several things, like one shopping order. " +
                "Smaller payments stay quiet.",
            style = MaterialTheme.typography.bodyMedium,
            color = Inkless.colors.muted,
        )
        PillChoice(
            labels = choices.map { it?.let(Money::format) ?: "Never" },
            selected = choices.indexOf(selected).coerceAtLeast(0),
            onSelect = { onSelect(choices[it]) },
        )
    }
}

@Composable
private fun ChangePasswordDialog(
    status: FormStatus,
    onConfirm: (current: String, new: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    val problem = if (new.isNotEmpty()) AuthValidation.passwordProblem(new) else null

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Inkless.colors.background,
        title = { Text("Change password", color = Inkless.colors.ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                PasswordField(current, { current = it }, enabled = !status.busy, label = "Current password")
                PasswordField(
                    new,
                    { new = it },
                    enabled = !status.busy,
                    label = "New password",
                    isNewPassword = true,
                    supporting = problem ?: "At least 8 characters, with a letter and a digit",
                )
                ErrorMessage(status.error)
            }
        },
        confirmButton = {
            TextLink(
                "Change",
                { onConfirm(current, new) },
                enabled = !status.busy && current.isNotEmpty() && new.isNotEmpty() && problem == null,
            )
        },
        dismissButton = { TextLink("Cancel", onDismiss, color = Inkless.colors.muted) },
    )
}

@Composable
private fun DeleteAccountDialog(
    usesPassword: Boolean,
    status: FormStatus,
    onConfirmPassword: (String) -> Unit,
    onConfirmGoogle: () -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Inkless.colors.background,
        title = { Text("Delete your account?", color = Inkless.colors.ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Text(
                    "This deletes your account, its cloud backup and the splits you shared with friends, frees your username and wipes your " +
                        "Inkless data from this phone. It can't be undone.",
                    color = Inkless.colors.muted,
                )
                if (usesPassword) {
                    PasswordField(password, { password = it }, enabled = !status.busy, label = "Confirm with your password")
                } else {
                    GoogleButton(onClick = onConfirmGoogle, enabled = !status.busy, text = "Confirm with Google")
                }
                ErrorMessage(status.error)
            }
        },
        confirmButton = {
            if (usesPassword) {
                TextLink(
                    "Delete",
                    { onConfirmPassword(password) },
                    enabled = !status.busy && password.isNotEmpty(),
                    color = Inkless.colors.danger,
                )
            }
        },
        dismissButton = { TextLink("Cancel", onDismiss, color = Inkless.colors.muted) },
    )
}
