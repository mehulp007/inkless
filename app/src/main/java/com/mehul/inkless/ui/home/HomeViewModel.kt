package com.mehul.inkless.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mehul.inkless.auth.AuthException
import com.mehul.inkless.auth.AuthRepository
import com.mehul.inkless.capture.sms.SmsCapture
import com.mehul.inkless.data.db.entity.CategoryEntity
import com.mehul.inkless.data.repo.LedgerRepository
import com.mehul.inkless.session.Session
import com.mehul.inkless.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: LedgerRepository,
    private val sessions: SessionManager,
    private val auth: AuthRepository,
    private val smsCapture: SmsCapture,
    private val clock: Clock,
) : ViewModel() {

    private val verificationSent = MutableStateFlow(false)

    val account: StateFlow<HomeAccount> =
        combine(sessions.session, verificationSent, smsCapture.settingsChanged) { session, sent, _ ->
            when (session) {
                Session.Demo -> HomeAccount(isDemo = true)
                is Session.SignedIn -> HomeAccount(
                    unverifiedEmail = session.user.email.takeUnless { session.user.emailVerified },
                    verificationSent = sent,
                    offerSmsSetup = !smsCapture.isEnabled(session.user.uid) && !smsCapture.setupSeen(session.user.uid),
                )
                else -> HomeAccount()
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeAccount())

    /** Payments waiting for a category, for the "to sort" card. */
    val toSort: StateFlow<Int> = repository.needsReview().map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val categories: StateFlow<List<CategoryEntity>> = repository.categories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addCash(amountPaise: Long, categoryId: String, note: String?) {
        val owner = sessions.currentOwnerUid() ?: return
        viewModelScope.launch { repository.addCash(amountPaise, categoryId, note, owner) }
    }

    private val selectedMonth = MutableStateFlow(YearMonth.now(clock))

    val uiState: StateFlow<HomeUiState> = selectedMonth
        .flatMapLatest { month ->
            val zone = clock.zone
            val start = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

            combine(
                repository.categories(),
                repository.transactionsBetween(start, end),
                repository.categoryTotalsBetween(start, end),
            ) { categories, transactions, totals ->
                HomeStateBuilder.build(
                    month = month,
                    today = LocalDate.now(clock),
                    zone = zone,
                    categories = categories,
                    transactions = transactions,
                    totals = totals,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

    fun refreshAccount() {
        if (account.value.unverifiedEmail != null) viewModelScope.launch { sessions.refreshUser() }
    }

    fun resendVerification() {
        verificationSent.value = true
        viewModelScope.launch {
            try {
                auth.sendVerificationEmail()
            } catch (e: AuthException) {
                verificationSent.value = false
            }
        }
    }

    fun previousMonth() = selectedMonth.update { it.minusMonths(1) }

    fun nextMonth() = selectedMonth.update { month ->
        if (month < YearMonth.now(clock)) month.plusMonths(1) else month
    }
}
