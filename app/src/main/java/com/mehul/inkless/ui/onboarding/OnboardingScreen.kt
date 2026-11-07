package com.mehul.inkless.ui.onboarding

import android.content.Context
import android.os.PowerManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mehul.inkless.R
import com.mehul.inkless.capture.notification.NotificationAccess
import com.mehul.inkless.ui.budget.AllowanceForm
import com.mehul.inkless.ui.budget.BudgetViewModel
import com.mehul.inkless.ui.budget.ordinal
import com.mehul.inkless.ui.capture.CaptureSetupCards
import com.mehul.inkless.ui.capture.ImportHistorySection
import com.mehul.inkless.ui.capture.ImportState
import com.mehul.inkless.ui.capture.SmsSetupViewModel
import com.mehul.inkless.ui.components.ChipTone
import com.mehul.inkless.ui.components.InklessCard
import com.mehul.inkless.ui.components.InklessTopBar
import com.mehul.inkless.ui.components.NavRow
import com.mehul.inkless.ui.components.PageHeadline
import com.mehul.inkless.ui.components.PrimaryButton
import com.mehul.inkless.ui.components.PrivacyNote
import com.mehul.inkless.ui.components.StatusChip
import com.mehul.inkless.ui.components.TextLink
import com.mehul.inkless.ui.components.TintCard
import com.mehul.inkless.ui.format.Money
import com.mehul.inkless.ui.theme.Inkless
import com.mehul.inkless.ui.theme.Spacing
import java.time.LocalDate

/** The first-run steps, in order. Importing past SMS is skipped when SMS access is off. */
enum class SetupStep {
    Capture, Allowance, Import, Finish;

    fun next(smsOn: Boolean): SetupStep = when (this) {
        Capture -> Allowance
        Allowance -> if (smsOn) Import else Finish
        Import, Finish -> Finish
    }

    /** Null on the first step, where back leaves the setup. */
    fun previous(smsOn: Boolean): SetupStep? = when (this) {
        Capture -> null
        Allowance -> Capture
        Import -> Allowance
        Finish -> if (smsOn) Import else Allowance
    }
}

/**
 * First thing after a new account: how payments get in, the monthly allowance, and past payments,
 * then a short "you're set" with anything still worth doing. All of it can be changed later.
 */
@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    onOpenHealth: () -> Unit,
    onOpenMyAccounts: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
    capture: SmsSetupViewModel = hiltViewModel(),
    budget: BudgetViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val smsGranted by capture.permissionGranted.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableStateOf(SetupStep.Capture) }

    val back = { step.previous(smsGranted)?.let { step = it } ?: onDone() }
    BackHandler(enabled = step.previous(smsGranted) != null) { back() }

    val (title, marker) = when (step) {
        SetupStep.Capture -> "Set up your ledger" to "1 of 3"
        SetupStep.Allowance -> "Make room for the month" to "2 of 3"
        SetupStep.Import -> "Bring your history along" to "3 of 3"
        SetupStep.Finish -> "All done" to null
    }

    Surface(Modifier.fillMaxSize(), color = Inkless.colors.background) {
        Column(Modifier.fillMaxSize()) {
            InklessTopBar(title, onBack = back, marker = marker)
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = Spacing.gutter, vertical = Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.xl),
            ) {
                when (step) {
                    SetupStep.Capture -> CaptureStep(
                        capture = capture,
                        granted = smsGranted,
                        onContinue = {
                            if (!smsGranted) capture.notNow()
                            step = step.next(smsGranted)
                        },
                    )
                    SetupStep.Allowance -> AllowanceStep(budget = budget, onContinue = { step = step.next(smsGranted) })
                    SetupStep.Import -> ImportStep(capture = capture, onContinue = { step = step.next(smsGranted) })
                    SetupStep.Finish -> FinishStep(
                        state = state,
                        smsOn = smsGranted,
                        onOpenMyAccounts = onOpenMyAccounts,
                        onOpenHealth = onOpenHealth,
                        onDone = onDone,
                    )
                }
            }
        }
    }
}

@Composable
private fun CaptureStep(capture: SmsSetupViewModel, granted: Boolean, onContinue: () -> Unit) {
    PageHeadline(
        "You pay. It's already logged.",
        lead = "Inkless reads your bank's payment alerts, so payments from any UPI app find their way here.",
    )
    CaptureSetupCards(capture)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        PrimaryButton("Continue", onClick = onContinue)
        if (!granted) Centered { TextLink("Skip for now · add payments by hand", onClick = onContinue) }
    }
}

@Composable
private fun AllowanceStep(budget: BudgetViewModel, onContinue: () -> Unit) {
    val form by budget.form.collectAsStateWithLifecycle()
    PageHeadline(
        "Your month. Your allowance.",
        lead = "Set the amount you live on. Inkless turns it into what you can spend each day.",
    )
    AllowanceForm(
        form = form,
        cycleFor = budget::cycleFor,
        onAmount = budget::setAmount,
        onDay = { budget.setDay(it.toString()) },
        onFoodCap = budget::setFoodCap,
    )
    Text(
        "The budget month runs from the day your allowance arrives. Category budgets follow calendar months.",
        style = MaterialTheme.typography.bodySmall,
        color = Inkless.colors.muted,
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        PrimaryButton("Save and continue", onClick = { budget.save(then = onContinue) }, enabled = form.canSave)
        Centered { TextLink("Set this up later", onClick = onContinue) }
    }
}

@Composable
private fun ImportStep(capture: SmsSetupViewModel, onContinue: () -> Unit) {
    val importState by capture.importState.collectAsStateWithLifecycle()
    val started by capture.importStarted.collectAsStateWithLifecycle()
    var months by rememberSaveable { mutableIntStateOf(1) }
    PageHeadline(
        "Start with the whole picture.",
        lead = "Add past payments from the bank SMS already on this phone.",
    )
    ImportHistorySection(
        state = importState,
        started = started,
        months = months,
        onMonths = { months = it },
        today = LocalDate.now(),
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        when {
            importState is ImportState.Running -> PrimaryButton("Continue in background", onClick = onContinue)
            started && importState is ImportState.Finished -> PrimaryButton("Continue", onClick = onContinue)
            else -> {
                PrimaryButton("Import", onClick = { capture.importHistory(months) })
                Centered { TextLink("Skip, start from today", onClick = onContinue) }
            }
        }
    }
}

@Composable
private fun FinishStep(
    state: OnboardingState,
    smsOn: Boolean,
    onOpenMyAccounts: () -> Unit,
    onOpenHealth: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    // Re-checked on return from a system setting.
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose {}
    }
    val phone = remember(refresh) { PhoneChecks(context) }

    PageHeadline("You're set.", lead = "Here's what's on. All of it can be changed later in Settings.")
    InklessCard(padding = PaddingValues(horizontal = Spacing.card, vertical = Spacing.s)) {
        StatusLine("Bank SMS", if (smsOn) "On" else "Off", if (smsOn) ChipTone.Good else ChipTone.Neutral)
        HorizontalDivider(color = Inkless.colors.hairline)
        StatusLine(
            "UPI notifications",
            if (phone.notifications) "On" else "Optional",
            if (phone.notifications) ChipTone.Good else ChipTone.Neutral,
        )
        HorizontalDivider(color = Inkless.colors.hairline)
        StatusLine(
            "Allowance",
            state.budget?.let { "${Money.format(it.amountPaise)} on the ${ordinal(it.day)}" } ?: "Not set",
            if (state.budget != null) ChipTone.Good else ChipTone.Neutral,
        )
    }
    if (!state.hasAccounts || !phone.battery) {
        TintCard(padding = PaddingValues(vertical = Spacing.xs)) {
            if (!state.hasAccounts) {
                NavRow(
                    "Add your UPI IDs",
                    subtitle = "So money moved between your own accounts isn't spending, and money from home is allowance.",
                    icon = R.drawable.icon_landmark,
                    onClick = onOpenMyAccounts,
                )
            }
            if (!phone.battery) {
                NavRow(
                    "Battery settings",
                    subtitle = "So Android doesn't put Inkless to sleep and miss a payment.",
                    icon = R.drawable.icon_battery_medium,
                    onClick = onOpenHealth,
                )
            }
        }
    }
    PrivacyNote(
        "What the backup holds.",
        body = "Only a payment's details: amount, payee, category. Never the SMS. It's in Firebase, closed to other users, " +
            "but whoever runs the Firebase project could see it. Don't want that? Switch backup off in Settings.",
        icon = R.drawable.icon_cloud_check,
    )
    PrimaryButton("Start using Inkless", onClick = onDone, icon = R.drawable.icon_arrow_right)
}

@Composable
private fun StatusLine(label: String, value: String, tone: ChipTone) {
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.m), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = Inkless.colors.ink)
        StatusChip(value, tone = tone)
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { content() }
}

private class PhoneChecks(context: Context) {
    val notifications = NotificationAccess.granted(context)
    val battery = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
}
