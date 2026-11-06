package com.mehul.inkless.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mehul.inkless.R
import com.mehul.inkless.auth.AuthValidation
import com.mehul.inkless.ui.auth.ErrorMessage
import com.mehul.inkless.ui.auth.FormStatus
import com.mehul.inkless.ui.auth.GoogleButton
import com.mehul.inkless.ui.auth.PasswordField

private enum class SettingsDialog { CHANGE_PASSWORD, DELETE_ACCOUNT, LOG_OUT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenSmsSetup: () -> Unit,
    onOpenUnread: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenMyAccounts: () -> Unit,
    onOpenHealth: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val capture by viewModel.capture.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.recheckPermission()
        onPauseOrDispose {}
    }
    val context = LocalContext.current
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val dialogStatus by viewModel.dialogStatus.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var dialog by rememberSaveable { mutableStateOf<SettingsDialog?>(null) }
    val version = remember {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    val closeDialog = {
        dialog = null
        viewModel.dialogClosed()
    }

    // Password changed: close the dialog and say so. (Deleting the account swaps the screen out instead.)
    LaunchedEffect(dialogStatus.done) {
        if (dialogStatus.done && dialog == SettingsDialog.CHANGE_PASSWORD) {
            closeDialog()
            snackbar.showSnackbar("Password changed")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            when (val current = mode) {
                SettingsMode.Demo -> {
                    ListItem(
                        headlineContent = { Text("Demo mode") },
                        supportingContent = { Text("Everything you see is made-up sample data.") },
                    )
                    ActionItem("Exit demo", onClick = viewModel::exitDemo)
                }
                is SettingsMode.Account -> {
                    SectionTitle("Account")
                    ListItem(
                        headlineContent = {
                            Text(current.username?.let { "@$it" } ?: "Your account", fontWeight = FontWeight.Medium)
                        },
                        supportingContent = { current.email?.let { Text(it) } },
                    )
                    if (!current.emailVerified) {
                        ListItem(
                            headlineContent = { Text("Email not verified") },
                            supportingContent = { Text("Click the link we emailed you.") },
                            trailingContent = {
                                TextButton(onClick = viewModel::resendVerification, enabled = !busy) {
                                    Text("Resend")
                                }
                            },
                        )
                    }
                    if (!current.hasGoogle) {
                        ActionItem(
                            "Link Google account",
                            supporting = "Then you can log in with either",
                            enabled = !busy,
                            onClick = { viewModel.linkGoogle(context) },
                        )
                    }
                    if (current.hasPassword) {
                        ActionItem("Change password", enabled = !busy) { dialog = SettingsDialog.CHANGE_PASSWORD }
                    }
                    ActionItem("Log out", enabled = !busy) { dialog = SettingsDialog.LOG_OUT }

                    SectionTitle("Auto-capture")
                    ActionItem(
                        "Bank SMS",
                        supporting = if (capture.smsOn) "On — payments are added from your bank's SMS" else "Off — tap to set up",
                        onClick = onOpenSmsSetup,
                    )
                    ActionItem(
                        "Capture health",
                        supporting = "SMS, app notifications, battery: what's on and what needs fixing",
                        onClick = onOpenHealth,
                    )
                    ActionItem(
                        "My accounts",
                        supporting = "Your own and home's UPI IDs, for transfers and allowance",
                        onClick = onOpenMyAccounts,
                    )
                    ActionItem(
                        "Categorising rules",
                        supporting = "Always file a UPI ID or name under a category",
                        onClick = onOpenRules,
                    )
                    ActionItem(
                        "Couldn't read these" + if (capture.unreadCount > 0) " (${capture.unreadCount})" else "",
                        supporting = "Bank messages Inkless didn't understand",
                        onClick = onOpenUnread,
                    )
                    ActionItem(
                        "Delete account",
                        supporting = "Removes your account and everything on this phone",
                        destructive = true,
                        enabled = !busy,
                    ) { dialog = SettingsDialog.DELETE_ACCOUNT }
                }
                SettingsMode.None -> Unit
            }
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Version") },
                supportingContent = { Text(version) },
            )
        }
    }

    val account = mode as? SettingsMode.Account
    when (dialog) {
        SettingsDialog.LOG_OUT -> AlertDialog(
            onDismissRequest = closeDialog,
            title = { Text("Log out?") },
            text = { Text("Your data stays on this phone and comes back when you log in again.") },
            confirmButton = { TextButton(onClick = { closeDialog(); viewModel.logOut() }) { Text("Log out") } },
            dismissButton = { TextButton(onClick = closeDialog) { Text("Cancel") } },
        )
        SettingsDialog.CHANGE_PASSWORD -> ChangePasswordDialog(
            status = dialogStatus,
            onConfirm = viewModel::changePassword,
            onDismiss = closeDialog,
        )
        SettingsDialog.DELETE_ACCOUNT -> if (account != null) {
            DeleteAccountDialog(
                usesPassword = account.hasPassword,
                status = dialogStatus,
                onConfirmPassword = viewModel::deleteWithPassword,
                onConfirmGoogle = { viewModel.deleteWithGoogle(context) },
                onDismiss = closeDialog,
            )
        }
        null -> Unit
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun ActionItem(
    title: String,
    supporting: String? = null,
    destructive: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    ListItem(
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        headlineContent = { Text(title, color = color) },
        supportingContent = supporting?.let { { Text(it) } },
    )
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
        title = { Text("Change password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            TextButton(
                onClick = { onConfirm(current, new) },
                enabled = !status.busy && current.isNotEmpty() && new.isNotEmpty() && problem == null,
            ) { Text("Change") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
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
        title = { Text("Delete your account?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "This deletes your account, frees your username and wipes your Inkless data from this " +
                        "phone. It can't be undone.",
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
                TextButton(
                    onClick = { onConfirmPassword(password) },
                    enabled = !status.busy && password.isNotEmpty(),
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
