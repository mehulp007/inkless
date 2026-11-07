package com.mehul.inkless.ui.health

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mehul.inkless.R
import com.mehul.inkless.data.repo.LedgerRepository
import com.mehul.inkless.ui.components.ChipTone
import com.mehul.inkless.ui.components.Footnote
import com.mehul.inkless.ui.components.IconTile
import com.mehul.inkless.ui.components.InklessCard
import com.mehul.inkless.ui.components.InklessTopBar
import com.mehul.inkless.ui.components.PageHeadline
import com.mehul.inkless.ui.components.PrimaryButton
import com.mehul.inkless.ui.components.SecondaryButton
import com.mehul.inkless.ui.components.StatusChip
import com.mehul.inkless.ui.components.TextLink
import com.mehul.inkless.ui.components.WarmCard
import com.mehul.inkless.ui.theme.Inkless
import com.mehul.inkless.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class CaptureHealthViewModel @Inject constructor(repository: LedgerRepository) : ViewModel() {
    val lastCapturedAt: StateFlow<Long?> = repository.lastCapturedAt()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/**
 * Capture fails silently when a permission is off or the phone kills the app in the background,
 * so this shows each piece with a button that goes straight to the setting that fixes it.
 */
@Composable
fun CaptureHealthScreen(
    onBack: () -> Unit,
    onOpenSmsSetup: () -> Unit,
    viewModel: CaptureHealthViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    // Bumped on resume and by "Check again" so every check re-runs after a trip to system settings.
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose {}
    }
    val health = remember(refresh) { CaptureHealth.read(context) }
    val lastCaptured by viewModel.lastCapturedAt.collectAsStateWithLifecycle()
    val open = { intent: Intent -> context.startActivity(intent) }

    CaptureHealthContent(
        health = health,
        lastCapturedAt = lastCaptured,
        nowMillis = remember(refresh, lastCaptured) { System.currentTimeMillis() },
        onBack = onBack,
        onOpenSmsSetup = onOpenSmsSetup,
        onOpenNotificationAccess = { open(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
        onOpenAppNotifications = {
            open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        },
        onOpenAppInfo = { open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())) },
        onOpenBattery = { open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) },
        onCheckAgain = { refresh++ },
    )
}

@Composable
fun CaptureHealthContent(
    health: CaptureHealth,
    lastCapturedAt: Long?,
    nowMillis: Long,
    onBack: () -> Unit,
    onOpenSmsSetup: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onOpenAppNotifications: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onOpenBattery: () -> Unit,
    onCheckAgain: () -> Unit,
    zone: ZoneId = ZoneId.systemDefault(),
    /** Android 13+ hides notification access from sideloaded apps until "restricted settings" are allowed. */
    restrictedSettings: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
) {
    val colors = Inkless.colors
    Surface(Modifier.fillMaxSize(), color = colors.background) {
        Column(Modifier.fillMaxSize()) {
            InklessTopBar("Capture health", onBack = onBack)
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.s, bottom = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.xl),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
                    IconTile(if (health.sms) R.drawable.icon_activity else R.drawable.icon_triangle_alert, size = 56.dp)
                    PageHeadline(health.headline, lead = health.summary)
                    Text(
                        HealthText.lastCaptured(lastCapturedAt, nowMillis, zone) +
                            " Notification access is optional; bank SMS remains your primary source.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.muted,
                    )
                }

                InklessCard {
                    Check(
                        title = "Bank SMS",
                        body = if (health.sms) "Payments are read from your bank's SMS." else "SMS access is off, so payments won't appear by themselves.",
                        ok = health.sms,
                        okText = "Allowed",
                        badText = "Off",
                        action = if (health.sms) null else "Set up" to onOpenSmsSetup,
                    )
                    HorizontalDivider(Modifier.padding(vertical = Spacing.m), color = colors.hairline)
                    Check(
                        title = "Inkless notifications",
                        body = if (health.questions) {
                            "Inkless can ask what a new payment was."
                        } else {
                            "Off, so new places wait in To sort instead of asking you."
                        },
                        ok = health.questions,
                        okText = "Allowed",
                        badText = "Blocked",
                        action = if (health.questions) null else "Allow" to onOpenAppNotifications,
                    )
                }

                InklessCard {
                    Check(
                        title = "UPI app notifications",
                        body = if (health.notificationAccess) {
                            "Watching ${health.installedApps.ifEmpty { listOf("UPI apps") }.joinToString()}: nicer names, and UPI Lite payments that send no SMS."
                        } else {
                            "Inkless still uses bank SMS, but names are less friendly and UPI Lite payments are missed."
                        },
                        ok = health.notificationAccess,
                        okText = "Allowed",
                        badText = "Blocked",
                    )
                    if (!health.notificationAccess) {
                        if (restrictedSettings) {
                            WarmCard(Modifier.padding(top = Spacing.l)) {
                                Text("For sideloaded apps on Android 13+", style = MaterialTheme.typography.titleSmall)
                                Column(Modifier.padding(top = Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                    Step(1, "Open Inkless's App info.")
                                    Step(2, "Tap ⋮ at the top right, then Allow restricted settings.")
                                    Step(3, "Come back and turn on notification access.")
                                }
                            }
                            SecondaryButton("Open App info", onOpenAppInfo, Modifier.padding(top = Spacing.l), icon = R.drawable.icon_external_link)
                        }
                        TextLink("Turn on notification access", onOpenNotificationAccess, Modifier.padding(top = Spacing.s))
                    }
                }

                InklessCard {
                    Check(
                        title = "Battery optimisation",
                        body = if (health.battery) {
                            "Android won't put Inkless to sleep in the background."
                        } else {
                            "Battery saving may stop Inkless in the background. Some phones (Xiaomi, Realme, Vivo, Oppo) also need " +
                                "Autostart switched on for Inkless in their own settings."
                        },
                        ok = health.battery,
                        okText = "Allowed",
                        badText = "Check",
                        badTone = ChipTone.Check,
                    )
                    if (!health.battery) {
                        SecondaryButton("Open battery settings", onOpenBattery, Modifier.padding(top = Spacing.l), icon = R.drawable.icon_battery_medium)
                    }
                }

                PrimaryButton("Check again", onCheckAgain, icon = R.drawable.icon_refresh_cw)
                Footnote("Settings names vary by phone. No bank login needed. Inkless observes payments; it never sends them.")
            }
        }
    }
}

@Composable
private fun Check(
    title: String,
    body: String,
    ok: Boolean,
    okText: String,
    badText: String,
    badTone: ChipTone = ChipTone.Check,
    action: Pair<String, () -> Unit>? = null,
) {
    val colors = Inkless.colors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = colors.ink)
            StatusChip(if (ok) okText else badText, tone = if (ok) ChipTone.Good else badTone)
        }
        Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.muted)
        action?.let { (label, onClick) -> TextLink(label, onClick) }
    }
}

@Composable
private fun Step(number: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text("$number.", style = MaterialTheme.typography.bodyMedium)
        Text(text, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium)
    }
}

object HealthText {
    private val time = DateTimeFormatter.ofPattern("HH:mm")
    private val day = DateTimeFormatter.ofPattern("d MMM")

    /** "Last payment captured today at 18:15." */
    fun lastCaptured(millis: Long?, nowMillis: Long, zone: ZoneId): String {
        if (millis == null) return "No payment captured yet."
        val at = Instant.ofEpochMilli(millis).atZone(zone)
        val days = ChronoUnit.DAYS.between(at.toLocalDate(), Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate())
        val whenText = when (days) {
            0L -> "today"
            1L -> "yesterday"
            else -> "on ${day.format(at)}"
        }
        return "Last payment captured $whenText at ${time.format(at)}."
    }
}
