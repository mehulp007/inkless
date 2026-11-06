package com.mehul.inkless.ui.capture

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mehul.inkless.R
import com.mehul.inkless.capture.sms.SmsPermissions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsSetupScreen(
    onDone: () -> Unit,
    viewModel: SmsSetupViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val granted by viewModel.permissionGranted.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val importStarted by viewModel.importStarted.collectAsStateWithLifecycle()
    var askedOnce by rememberSaveable { mutableStateOf(false) }
    var months by rememberSaveable { mutableIntStateOf(3) }

    // Runtime permissions: Android shows its own "Allow Inkless to send and view SMS?" dialog,
    // and we get the answer back here.
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        askedOnce = true
        viewModel.refreshPermission(result.values.all { it })
    }

    // The user might flip the permission in system settings and come back.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshPermission()
        onPauseOrDispose {}
    }

    // After "Don't allow" twice Android stops showing the dialog; only system settings can change it then.
    val blocked = askedOnce && !granted && activity != null &&
        SmsPermissions.required.none { activity.shouldShowRequestPermissionRationale(it) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Auto-capture") },
                navigationIcon = {
                    IconButton(onClick = { if (!granted) viewModel.notNow(); onDone() }) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Your bank sends an SMS for every UPI payment. Inkless can read those and add the payments for you, " +
                    "no typing needed.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Promise("Only bank payment alerts are kept. OTPs, offers and personal messages are skipped and never saved.")
            Promise("The message text stays on this phone. Inkless doesn't upload your SMS anywhere.")
            Promise("You can switch it off any time in Android Settings → Apps → Inkless → Permissions.")

            if (!granted) {
                if (blocked) {
                    Text(
                        "SMS access was turned down. You can allow it from Inkless's page in system settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) { Text("Open app settings") }
                } else {
                    Button(
                        onClick = { launcher.launch(SmsPermissions.required) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) { Text("Allow SMS access") }
                }
                TextButton(onClick = { viewModel.notNow(); onDone() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Not now")
                }
            } else {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Text(
                        "✓ New payments will now appear on their own.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                ImportSection(
                    state = importState,
                    started = importStarted,
                    months = months,
                    onMonths = { months = it },
                    onImport = { viewModel.importHistory(months) },
                    onDone = onDone,
                )
            }
        }
    }
}

@Composable
private fun Promise(text: String) {
    Row {
        Icon(
            painterResource(R.drawable.ic_check_circle),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(text, modifier = Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ImportSection(
    state: ImportState,
    started: Boolean,
    months: Int,
    onMonths: (Int) -> Unit,
    onImport: () -> Unit,
    onDone: () -> Unit,
) {
    Text("Add past payments too?", style = MaterialTheme.typography.titleMedium)

    when {
        state is ImportState.Running -> {
            val progress = if (state.total > 0) state.done.toFloat() / state.total else null
            if (progress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            else LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Text(
                if (state.total > 0) "Reading ${state.done} of ${state.total} messages…" else "Starting…",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        started && state is ImportState.Finished -> {
            val found = if (state.saved == 1) "1 payment" else "${state.saved} payments"
            Text("Found $found in ${state.scanned} messages.", style = MaterialTheme.typography.bodyLarge)
            if (state.unparsed > 0) {
                Text(
                    "${state.unparsed} bank messages couldn't be read. You'll find them in Settings → Couldn't read these.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Done") }
        }
        else -> {
            if (state is ImportState.Failed) {
                Text("The import didn't finish. Try again.", color = MaterialTheme.colorScheme.error)
            }
            Text(
                "Inkless can go through the SMS already on your phone and add those payments as well.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 3, 6).forEach { option ->
                    FilterChip(
                        selected = months == option,
                        onClick = { onMonths(option) },
                        label = { Text(if (option == 1) "Last month" else "Last $option months") },
                    )
                }
            }
            Button(onClick = onImport, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Import") }
            OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Skip") }
        }
    }
}
