package com.mehul.inkless.ui.budget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mehul.inkless.ui.components.ConfirmDialog
import com.mehul.inkless.ui.components.InklessTopBar
import com.mehul.inkless.ui.components.PageHeadline
import com.mehul.inkless.ui.components.PrimaryButton
import com.mehul.inkless.ui.components.TextLink
import com.mehul.inkless.ui.components.ToggleRow
import com.mehul.inkless.ui.theme.Inkless
import com.mehul.inkless.ui.theme.Spacing

/** The monthly allowance, the day it arrives and a daily food cap: everything the budget works from. */
@Composable
fun BudgetScreen(
    onBack: () -> Unit,
    viewModel: BudgetViewModel = hiltViewModel(),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    var stopping by rememberSaveable { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize(), color = Inkless.colors.background) {
        Column(Modifier.fillMaxSize()) {
            InklessTopBar("Allowance", onBack = onBack)
            if (form.loading) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Inkless.colors.primary) }
                return@Column
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = Spacing.gutter, vertical = Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.xl),
            ) {
                PageHeadline(
                    "Your month. Your allowance.",
                    lead = "What you live on each month. Inkless spreads what's left over the days until the next one, so Home can say what's safe to spend today.",
                )
                AllowanceForm(
                    form = form,
                    cycleFor = viewModel::cycleFor,
                    onAmount = viewModel::setAmount,
                    onDay = { viewModel.setDay(it.toString()) },
                    onFoodCap = viewModel::setFoodCap,
                )
                ToggleRow(
                    title = "Weekly summary · Sundays",
                    subtitle = "The week's spending and what's left, as a notification",
                    checked = form.weeklySummary,
                    onCheckedChange = viewModel::setWeeklySummary,
                )
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    PrimaryButton("Save", onClick = { viewModel.save(then = onBack) }, enabled = form.canSave)
                    if (form.hasBudget) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            TextLink("Stop budgeting", onClick = { stopping = true }, color = Inkless.colors.danger)
                        }
                    }
                }
            }
        }
    }

    if (stopping) {
        ConfirmDialog(
            title = "Stop budgeting?",
            body = "Home stops showing what's safe to spend. Your payments and category budgets stay as they are, and you can set an allowance again any time.",
            confirmText = "Stop",
            onConfirm = { stopping = false; viewModel.remove(then = onBack) },
            onDismiss = { stopping = false },
            destructive = true,
        )
    }
}
