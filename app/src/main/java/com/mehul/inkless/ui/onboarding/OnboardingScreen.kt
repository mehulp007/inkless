package com.mehul.inkless.ui.onboarding

import android.content.Context
import android.os.PowerManager
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mehul.inkless.R
import com.mehul.inkless.capture.notification.NotificationAccess
import com.mehul.inkless.capture.sms.SmsPermissions
import com.mehul.inkless.ui.theme.LocalInklessColors

/**
 * First thing after a new account: what Inkless does with your data, then the few things that make
 * it work, each with its state and a button to the screen that sets it up. Everything can be done
 * later from Settings too.
 */
@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    onOpenSmsSetup: () -> Unit,
    onOpenHealth: () -> Unit,
    onOpenBudget: () -> Unit,
    onOpenMyAccounts: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Re-checked on return from each setup screen or system setting.
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose {}
    }
    val phone = remember(refresh) { PhoneChecks(context) }

    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Welcome to Inkless", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "You pay, it's already logged. Inkless reads the SMS your bank sends for every UPI payment and files it for you.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Before you start", style = MaterialTheme.typography.titleSmall)
                    Bullet("Only bank payment alerts are read. OTPs, offers and personal messages are thrown away unread.")
                    Bullet("The text of an SMS never leaves this phone. Only the payment's details are backed up: amount, payee, category.")
                    Bullet("The backup is in Firebase, encrypted by Google and closed to other users, but whoever runs the Firebase project could see it. Don't want that? Switch backup off in Settings.")
                }
            }

            Text("Get set up", style = MaterialTheme.typography.titleMedium)
            Step(
                done = state.smsOn && phone.sms,
                title = "1. Read bank SMS",
                text = "The main switch: payments appear by themselves, and past months can be imported.",
                button = "Set up",
                onClick = onOpenSmsSetup,
            )
            Step(
                done = phone.notifications && phone.battery,
                title = "2. UPI app notifications and battery",
                text = "Optional but worth it: friendlier names, UPI Lite payments, and Android not putting Inkless to sleep. " +
                    "On Android 13+, an app installed from a file needs \"Allow restricted settings\" first; the next screen shows where.",
                button = "Check",
                onClick = onOpenHealth,
            )
            Step(
                done = state.budgetSet,
                title = "3. Your monthly allowance",
                text = "Then Home says what's safe to spend today.",
                button = "Set",
                onClick = onOpenBudget,
            )
            Step(
                done = state.hasAccounts,
                title = "4. Your own UPI IDs",
                text = "So moving money between your own accounts isn't counted as spending, and money from home is.",
                button = "Add",
                onClick = onOpenMyAccounts,
            )
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text(if (state.smsOn) "Start using Inkless" else "Skip for now")
            }
        }
    }
}

@Composable
private fun Step(done: Boolean, title: String, text: String, button: String, onClick: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f, fill = false))
                    if (done) {
                        Icon(
                            painterResource(R.drawable.ic_check_circle),
                            contentDescription = "Done",
                            tint = LocalInklessColors.current.credit,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
                Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!done) OutlinedButton(onClick = onClick, modifier = Modifier.padding(start = 12.dp)) { Text(button) }
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Row {
        Text("•  ", style = MaterialTheme.typography.bodyMedium)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

private class PhoneChecks(context: Context) {
    val sms = SmsPermissions.granted(context)
    val notifications = NotificationAccess.granted(context)
    val battery = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
}
