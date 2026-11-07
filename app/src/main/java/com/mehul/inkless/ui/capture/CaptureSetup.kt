package com.mehul.inkless.ui.capture

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mehul.inkless.R
import com.mehul.inkless.capture.notification.NotificationAccess
import com.mehul.inkless.capture.sms.SmsPermissions
import com.mehul.inkless.ui.components.ChipTone
import com.mehul.inkless.ui.components.IconTile
import com.mehul.inkless.ui.components.InklessCard
import com.mehul.inkless.ui.components.Overline
import com.mehul.inkless.ui.components.PillChoice
import com.mehul.inkless.ui.components.PrimaryButton
import com.mehul.inkless.ui.components.PrivacyNote
import com.mehul.inkless.ui.components.ProgressBar
import com.mehul.inkless.ui.components.SecondaryButton
import com.mehul.inkless.ui.components.StatusChip
import com.mehul.inkless.ui.components.TextLink
import com.mehul.inkless.ui.components.TintCard
import com.mehul.inkless.ui.theme.Inkless
import com.mehul.inkless.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * The two ways payments arrive by themselves, each with its state and the button that turns it on,
 * plus what's read and how to get past Android 13's lock on apps installed from a file.
 */
@Composable
fun CaptureSetupCards(viewModel: SmsSetupViewModel, modifier: Modifier = Modifier) {
    val granted by viewModel.permissionGranted.collectAsStateWithLifecycle()
    CaptureSetupCards(
        granted = granted,
        onSmsAnswer = { answer -> if (answer == null) viewModel.refreshPermission() else viewModel.refreshPermission(answer) },
        modifier = modifier,
    )
}

/** [onSmsAnswer] gets the permission dialog's answer, or null when it's worth checking again. */
@Composable
fun CaptureSetupCards(granted: Boolean, onSmsAnswer: (Boolean?) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    var askedOnce by rememberSaveable { mutableStateOf(false) }

    // Runtime permissions: Android shows its own "Allow Inkless to send and view SMS?" dialog,
    // and the answer comes back here.
    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        askedOnce = true
        onSmsAnswer(result.values.all { it })
    }
    // Android 13+ needs a runtime "allow notifications" for the "what was this?" questions.
    var notificationsAllowed by remember { mutableStateOf(notificationsGranted(context)) }
    var listenerOn by remember { mutableStateOf(NotificationAccess.granted(context)) }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsAllowed = it
    }
    // The user might flip a switch in system settings and come back.
    LifecycleResumeEffect(Unit) {
        onSmsAnswer(null)
        notificationsAllowed = notificationsGranted(context)
        listenerOn = NotificationAccess.granted(context)
        onPauseOrDispose {}
    }
    // After "Don't allow" twice Android stops showing the dialog; only system settings can change it then.
    val blocked = askedOnce && !granted && activity != null &&
        SmsPermissions.required.none { activity.shouldShowRequestPermissionRationale(it) }
    val openAppInfo = {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
        SourceCard(
            icon = R.drawable.icon_message_square,
            title = "Bank SMS",
            subtitle = "Recommended · most reliable",
            chip = if (granted) "Allowed" else null,
            body = "Reads the amount, payee, date and account's last four digits from your bank's payment alerts.",
        ) {
            when {
                granted -> {
                    Text("New payments now appear on their own.", style = MaterialTheme.typography.bodyMedium, color = Inkless.colors.primary)
                    if (!notificationsAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        TextLink(
                            "Ask me what new payments were →",
                            onClick = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                        )
                    }
                }
                blocked -> {
                    Text(
                        "SMS access is off. Allow it from Inkless's page in system settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Inkless.colors.danger,
                    )
                    SecondaryButton("Open app settings", onClick = openAppInfo)
                }
                else -> PrimaryButton("Allow SMS access", onClick = { smsLauncher.launch(SmsPermissions.required) })
            }
        }
        SourceCard(
            icon = R.drawable.icon_bell,
            title = "UPI notifications",
            subtitle = "GPay, PhonePe and Paytm",
            chip = if (listenerOn) "Allowed" else "Optional",
            chipTone = if (listenerOn) ChipTone.Good else ChipTone.Neutral,
            body = "Adds the shop's real name and UPI Lite payments, which send no SMS. An SMS and a notification for the same payment become one.",
        ) {
            if (!listenerOn) {
                TextLink(
                    "Set up notification access →",
                    onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                )
            }
        }
        PrivacyNote(
            "Raw alert text stays on your phone.",
            body = "OTPs, offers and personal messages are skipped and never saved.",
        )
        // Android 13+ locks SMS and notification access for apps installed from a file until the
        // user unlocks "restricted settings"; the Allow option is greyed out until then.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && (!granted || !listenerOn)) {
            TintCard {
                Text("Android 13+ greyed out the switch?", style = MaterialTheme.typography.titleSmall, color = Inkless.colors.ink)
                Text(
                    "App info → ⋮ → Allow restricted settings. Then come back and allow SMS, or turn on notification access for Inkless.",
                    modifier = Modifier.padding(top = Spacing.xs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Inkless.colors.muted,
                )
                TextLink("Open app info →", onClick = openAppInfo)
            }
        }
    }
}

@Composable
private fun SourceCard(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    chip: String?,
    body: String,
    chipTone: ChipTone = ChipTone.Good,
    action: @Composable () -> Unit,
) {
    InklessCard {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Icon(painterResource(icon), contentDescription = null, tint = Inkless.colors.primary, modifier = Modifier.padding(top = 2.dp).size(22.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = Inkless.colors.ink)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Inkless.colors.muted)
                }
                if (chip != null) StatusChip(chip, tone = chipTone)
            }
            Text(body, style = MaterialTheme.typography.bodyMedium, color = Inkless.colors.muted)
            action()
        }
    }
}

private fun notificationsGranted(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

val IMPORT_MONTHS = listOf(1, 3, 6)

/**
 * Past payments from the SMS already on the phone: how far back, then the import's progress and
 * what it found. The buttons under it are the caller's, since they depend on where it's shown.
 */
@Composable
fun ImportHistorySection(
    state: ImportState,
    started: Boolean,
    months: Int,
    onMonths: (Int) -> Unit,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    val colors = Inkless.colors
    val running = state is ImportState.Running
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
        if (!running && !(started && state is ImportState.Finished)) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Overline("History to import")
                PillChoice(
                    labels = IMPORT_MONTHS.map { if (it == 1) "1 month" else "$it months" },
                    selected = IMPORT_MONTHS.indexOf(months).coerceAtLeast(0),
                    onSelect = { onMonths(IMPORT_MONTHS[it]) },
                )
                Text(
                    "${today.minusMonths(months.toLong()).plusDays(1).format(DAY)} – ${today.format(DAY_YEAR)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                )
            }
        }
        when {
            state is ImportState.Running -> InklessCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    IconTile(R.drawable.icon_scan_line)
                    Column(Modifier.weight(1f)) {
                        Text("Reading bank alerts…", style = MaterialTheme.typography.titleMedium, color = colors.ink)
                        Text(
                            if (state.total > 0) "${state.done} of ${state.total} messages checked" else "Starting…",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.muted,
                        )
                    }
                    if (state.total > 0) {
                        Text("${state.done * 100 / state.total}%", style = MaterialTheme.typography.labelLarge, color = colors.primary)
                    }
                }
                ProgressBar(
                    fraction = if (state.total > 0) state.done.toFloat() / state.total else 0f,
                    modifier = Modifier.padding(top = Spacing.l),
                )
            }
            started && state is ImportState.Finished -> InklessCard {
                Row(Modifier.fillMaxWidth()) {
                    Stat(state.saved.toString(), if (state.saved == 1) "payment found" else "payments found", Modifier.weight(1f))
                    Stat(state.unparsed.toString(), "couldn't be read", Modifier.weight(1f))
                }
                Text(
                    if (state.unparsed > 0) {
                        "From ${state.scanned} messages. The ones Inkless couldn't read are in Settings → Couldn't read these."
                    } else {
                        "From ${state.scanned} messages."
                    },
                    modifier = Modifier.padding(top = Spacing.m),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                )
            }
            state is ImportState.Failed -> Text("The import didn't finish. Try again.", style = MaterialTheme.typography.bodyMedium, color = colors.danger)
        }
        TintCard {
            Text("All reading happens on this phone.", style = MaterialTheme.typography.titleSmall, color = colors.ink)
            Text(
                "Only bank payment alerts are processed. OTPs and personal messages are ignored. Payments already in your ledger aren't added twice.",
                modifier = Modifier.padding(top = Spacing.xs),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted,
            )
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = Inkless.type.moneyMedium, color = Inkless.colors.ink)
        Text(label, style = MaterialTheme.typography.bodySmall, color = Inkless.colors.muted)
    }
}

private val DAY = DateTimeFormatter.ofPattern("d MMM")
private val DAY_YEAR = DateTimeFormatter.ofPattern("d MMM yyyy")
