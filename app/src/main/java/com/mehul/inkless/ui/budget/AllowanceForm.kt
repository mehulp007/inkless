package com.mehul.inkless.ui.budget

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mehul.inkless.R
import com.mehul.inkless.budget.Cycle
import com.mehul.inkless.ui.components.InklessBottomSheet
import com.mehul.inkless.ui.components.InklessCard
import com.mehul.inkless.ui.components.InklessTextField
import com.mehul.inkless.ui.components.ToggleRow
import com.mehul.inkless.ui.theme.Inkless
import com.mehul.inkless.ui.theme.Spacing
import java.time.format.DateTimeFormatter

/**
 * The allowance, the day it arrives and the optional daily food cap: the form under the first-run
 * step and the allowance screen.
 */
@Composable
fun AllowanceForm(
    form: BudgetForm,
    cycleFor: (Int?) -> Cycle,
    onAmount: (String) -> Unit,
    onDay: (Int) -> Unit,
    onFoodCap: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var capOn by rememberSaveable { mutableStateOf(false) }
    // The saved budget loads after the first frame; a cap that's already set opens the card.
    LaunchedEffect(form.loading) { if (!form.loading && form.foodCapText.isNotBlank()) capOn = true }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
        InklessTextField(
            value = form.amountText,
            onValueChange = onAmount,
            label = "Monthly allowance · you choose this",
            prefix = "₹",
            money = true,
            note = "The amount you live on each month. Change it any time.",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        DayOfMonthField(day = form.day, cycle = cycleFor(form.day), onDay = onDay)
        InklessCard {
            ToggleRow(
                title = "Daily food cap",
                subtitle = "Optional · a nudge, not a limit",
                checked = capOn,
                onCheckedChange = { on ->
                    capOn = on
                    if (!on) onFoodCap("")
                },
            )
            if (capOn) {
                InklessTextField(
                    value = form.foodCapText,
                    onValueChange = onFoodCap,
                    modifier = Modifier.padding(top = Spacing.s),
                    label = "Food & Canteen + Food Delivery",
                    prefix = "₹",
                    placeholder = "200",
                    note = "A day. Days over it count as mess skipped.",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        }
    }
}

/** Looks like a field ("1st of every month"); tapping it opens a sheet with the 31 days. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun DayOfMonthField(day: Int?, cycle: Cycle, onDay: (Int) -> Unit) {
    val colors = Inkless.colors
    var picking by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text("When does it arrive?", style = MaterialTheme.typography.labelMedium, color = colors.muted)
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = colors.surface,
            border = BorderStroke(1.dp, colors.hairline),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(role = Role.Button, onClickLabel = "Choose the day") { picking = true },
        ) {
            Row(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.l), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    day?.let { "${ordinal(it)} of every month" } ?: "Choose a day",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.ink,
                )
                Icon(painterResource(R.drawable.icon_calendar_days), contentDescription = null, tint = colors.muted, modifier = Modifier.size(20.dp))
            }
        }
        Text(
            "This budget month: ${cycleLabel(cycle)}",
            style = MaterialTheme.typography.bodySmall,
            color = colors.muted,
        )
    }

    if (picking) {
        InklessBottomSheet(onDismiss = { picking = false }) {
            Text("Which day does it usually arrive?", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                (1..31).forEach { option ->
                    val selected = option == day
                    Surface(
                        shape = CircleShape,
                        color = if (selected) colors.primary else colors.surface,
                        contentColor = if (selected) colors.onPrimary else colors.ink,
                        border = if (selected) null else BorderStroke(1.dp, colors.hairline),
                        modifier = Modifier
                            .size(44.dp)
                            .clickable(role = Role.RadioButton) {
                                onDay(option)
                                picking = false
                            },
                    ) {
                        Box(contentAlignment = Alignment.Center) { Text(option.toString(), style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
            Text(
                "In a shorter month, a late day counts as the month's last day.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted,
            )
        }
    }
}

private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM")
private val DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("d MMM yyyy")

/** "1 Nov – 30 Nov 2026" */
internal fun cycleLabel(cycle: Cycle): String {
    val last = cycle.endExclusive.minusDays(1)
    return "${cycle.start.format(DAY_MONTH)} – ${last.format(DAY_MONTH_YEAR)}"
}

/** 1st, 2nd, 3rd, 4th … 11th, 12th, 13th … 21st */
internal fun ordinal(day: Int): String {
    val suffix = if (day % 100 in 11..13) "th" else when (day % 10) {
        1 -> "st"
        2 -> "nd"
        3 -> "rd"
        else -> "th"
    }
    return "$day$suffix"
}
