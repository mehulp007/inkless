package com.mehul.inkless.ui.health

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.mehul.inkless.R
import com.mehul.inkless.capture.notification.NotificationAccess
import com.mehul.inkless.capture.sms.SmsPermissions
import com.mehul.inkless.parsing.UpiApp

/**
 * Capture fails silently when a permission is off or the phone kills the app in the background,
 * so this shows each piece with a button that goes straight to the setting that fixes it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureHealthScreen(
    onBack: () -> Unit,
    onOpenSmsSetup: () -> Unit,
) {
    val context = LocalContext.current
    // Bumped on resume so every check re-runs after a trip to system settings.
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose {}
    }
    val checks = remember(refresh) { HealthChecks(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Capture health") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Check(
                ok = checks.sms,
                title = "Bank SMS",
                okText = "Payments are read from your bank's SMS.",
                problem = "SMS access is off, so payments won't appear by themselves.",
                action = "Set up" to onOpenSmsSetup,
            )
            Check(
                ok = checks.notificationAccess,
                title = "UPI app notifications",
                okText = "Watching ${checks.installedApps.ifEmpty { listOf("UPI apps") }.joinToString()}: nicer names, and UPI Lite payments that send no SMS.",
                problem = "Notification access is off. Inkless can still use bank SMS, but names are less friendly and UPI Lite payments are missed." +
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        " If Inkless is greyed out in the list, open Inkless's app info → ⋮ → Allow restricted settings first."
                    } else {
                        ""
                    },
                action = "Turn on" to { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
            )
            Check(
                ok = checks.questions,
                title = "Category questions",
                okText = "Inkless can ask what a new payment was.",
                problem = "Notifications from Inkless are off, so new merchants wait in \"to sort\" instead.",
                action = "Allow" to {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                },
            )
            Check(
                ok = checks.battery,
                title = "Battery",
                okText = "Android won't put Inkless to sleep.",
                problem = "Battery saving may stop Inkless in the background. Some phones (Xiaomi, Realme, Vivo, Oppo) also need " +
                    "\"Autostart\" switched on for Inkless in their own settings app.",
                action = "Don't restrict" to { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) },
            )
            OutlinedButton(
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Open Inkless's app info") }
        }
    }
}

private class HealthChecks(context: Context) {
    val sms = SmsPermissions.granted(context)
    val notificationAccess = NotificationAccess.granted(context)
    val questions = NotificationManagerCompat.from(context).areNotificationsEnabled()
    val battery = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    val installedApps = UpiApp.entries.filter { isInstalled(context, it.packageName) }.map { it.label }

    private fun isInstalled(context: Context, packageName: String) =
        runCatching { context.packageManager.getPackageInfo(packageName, 0) }.isSuccess
}

@Composable
private fun Check(ok: Boolean, title: String, okText: String, problem: String, action: Pair<String, () -> Unit>) {
    val colors = if (ok) {
        CardDefaults.cardColors()
    } else {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    }
    Card(Modifier.fillMaxWidth(), colors = colors) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text((if (ok) "✓  " else "⚠  ") + title, style = MaterialTheme.typography.titleSmall)
            Text(if (ok) okText else problem, style = MaterialTheme.typography.bodyMedium)
            if (!ok) OutlinedButton(onClick = action.second) { Text(action.first) }
        }
    }
}
