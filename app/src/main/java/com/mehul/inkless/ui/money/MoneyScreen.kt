package com.mehul.inkless.ui.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mehul.inkless.R
import com.mehul.inkless.ui.components.InklessCard
import com.mehul.inkless.ui.components.InklessTopBar
import com.mehul.inkless.ui.components.NavRow
import com.mehul.inkless.ui.components.PageHeadline
import com.mehul.inkless.ui.theme.Inkless
import com.mehul.inkless.ui.theme.Spacing

/** The Money tab: the allowance and budgets, and the screens that look back over spending. */
@Composable
fun MoneyScreen(
    onOpenBudget: () -> Unit,
    onOpenInsights: () -> Unit,
    onOpenAsk: () -> Unit,
    onOpenWrapped: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        InklessTopBar("Money", icon = R.drawable.icon_chart_trend)
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter, vertical = Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.section),
        ) {
            PageHeadline(
                "Where the month is going.",
                lead = "Your allowance, what's left of it, and what your spending has been up to.",
            )
            InklessCard(padding = PaddingValues(vertical = Spacing.xs)) {
                NavRow(
                    "Allowance and budgets",
                    subtitle = "What you live on and when it arrives",
                    icon = R.drawable.icon_wallet,
                    onClick = onOpenBudget,
                )
                Divider()
                NavRow(
                    "Insights",
                    subtitle = "Spending alerts, what's coming up, how well Inkless guesses",
                    icon = R.drawable.icon_lightbulb,
                    onClick = onOpenInsights,
                )
                Divider()
                NavRow(
                    "Ask Inkless",
                    subtitle = "\"How much on food last month?\"",
                    icon = R.drawable.icon_message_question,
                    onClick = onOpenAsk,
                )
                Divider()
                NavRow(
                    "Wrapped",
                    subtitle = "Last month in a few numbers",
                    icon = R.drawable.icon_sparkles,
                    onClick = onOpenWrapped,
                )
            }
        }
    }
}

@Composable
private fun Divider() = HorizontalDivider(Modifier.padding(horizontal = Spacing.l), color = Inkless.colors.hairline)
