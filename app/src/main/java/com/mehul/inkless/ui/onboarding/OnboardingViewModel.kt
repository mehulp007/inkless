package com.mehul.inkless.ui.onboarding

import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mehul.inkless.capture.sms.SmsCapture
import com.mehul.inkless.data.repo.LedgerRepository
import com.mehul.inkless.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/** Whether an account has been through the first-run steps on this phone. */
@Singleton
class OnboardingPrefs @Inject constructor(@param:ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("onboarding", Context.MODE_PRIVATE)

    fun shown(uid: String) = prefs.getBoolean(uid, false)

    fun markShown(uid: String) = prefs.edit { putBoolean(uid, true) }
}

/** The allowance as the finish step shows it: "₹12,000 on the 1st". */
data class AllowanceSummary(val amountPaise: Long, val day: Int)

data class OnboardingState(val smsOn: Boolean = false, val budget: AllowanceSummary? = null, val hasAccounts: Boolean = false)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    sessions: SessionManager,
    smsCapture: SmsCapture,
    repository: LedgerRepository,
) : ViewModel() {

    val state: StateFlow<OnboardingState> = combine(
        smsCapture.settingsChanged,
        repository.budget(),
        repository.myAccounts(),
    ) { _, budget, accounts ->
        OnboardingState(
            smsOn = sessions.currentOwnerUid()?.let(smsCapture::isEnabled) ?: false,
            budget = budget?.let { AllowanceSummary(it.amountPaise, it.allowanceDay ?: 1) },
            hasAccounts = accounts.isNotEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OnboardingState())
}
