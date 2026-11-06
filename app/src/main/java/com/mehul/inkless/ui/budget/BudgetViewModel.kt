package com.mehul.inkless.ui.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mehul.inkless.budget.BudgetMath
import com.mehul.inkless.budget.DigestScheduler
import com.mehul.inkless.data.repo.LedgerRepository
import com.mehul.inkless.parsing.Amounts
import com.mehul.inkless.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class BudgetForm(
    val loading: Boolean = true,
    val hasBudget: Boolean = false,
    val amountText: String = "",
    val dayText: String = "1",
    val foodCapText: String = "",
    val weeklySummary: Boolean = true,
) {
    val amountPaise: Long? get() = Amounts.toPaise(amountText)?.takeIf { it > 0 }
    val day: Int? get() = dayText.toIntOrNull()?.takeIf { it in 1..31 }
    val foodCapPaise: Long? get() = Amounts.toPaise(foodCapText)?.takeIf { it > 0 }
    val canSave: Boolean get() = amountPaise != null && day != null && (foodCapText.isBlank() || foodCapPaise != null)
}

@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val repository: LedgerRepository,
    private val sessions: SessionManager,
    private val digest: DigestScheduler,
    clock: Clock,
) : ViewModel() {

    private val _form = MutableStateFlow(BudgetForm())
    val form: StateFlow<BudgetForm> = _form.asStateFlow()

    private val today = LocalDate.now(clock)
    val semesterName: String = BudgetMath.semesterName(today)

    /** Spending this semester (July–December or January–June), counted like the category totals. */
    val semesterSpentPaise: StateFlow<Long> = BudgetMath.semester(today).let { (start, end) ->
        repository.spendBetween(start.atStartOfDay(clock.zone).toInstant().toEpochMilli(), end.atStartOfDay(clock.zone).toInstant().toEpochMilli())
            .map { parts -> parts.sumOf { it.amountPaise } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)
    }

    init {
        viewModelScope.launch {
            val budget = repository.budget().first()
            _form.value = BudgetForm(
                loading = false,
                hasBudget = budget != null,
                amountText = budget?.amountPaise?.let(::rupees).orEmpty(),
                dayText = (budget?.allowanceDay ?: 1).toString(),
                foodCapText = budget?.dailyFoodCapPaise?.let(::rupees).orEmpty(),
                weeklySummary = digest.enabled,
            )
        }
    }

    fun setAmount(text: String) = _form.update { it.copy(amountText = digitsOnly(text)) }
    fun setDay(text: String) = _form.update { it.copy(dayText = text.filter(Char::isDigit).take(2)) }
    fun setFoodCap(text: String) = _form.update { it.copy(foodCapText = digitsOnly(text)) }

    fun setWeeklySummary(on: Boolean) {
        digest.enabled = on
        _form.update { it.copy(weeklySummary = on) }
    }

    fun save(then: () -> Unit) {
        val form = _form.value
        val amount = form.amountPaise ?: return
        val owner = sessions.currentOwnerUid() ?: return
        viewModelScope.launch {
            repository.saveBudget(amount, form.day, form.foodCapPaise, owner)
            then()
        }
    }

    fun remove(then: () -> Unit) {
        viewModelScope.launch {
            repository.removeBudget()
            then()
        }
    }

    private fun digitsOnly(text: String) = text.filter { it.isDigit() || it == '.' }.take(9)

    private fun rupees(paise: Long) = if (paise % 100 == 0L) (paise / 100).toString() else "%d.%02d".format(paise / 100, paise % 100)
}
