package com.mehul.inkless.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mehul.inkless.R
import com.mehul.inkless.ui.theme.Inkless
import com.mehul.inkless.ui.theme.InklessTheme
import com.mehul.inkless.ui.theme.Spacing

/**
 * Every shared component on one page, for Android Studio's preview and the screenshot test that
 * guards them. Not shown anywhere in the app.
 */
@Composable
internal fun KitGallery() {
    Column(
        Modifier
            .background(Inkless.colors.background)
            .width(412.dp)
            .padding(Spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        InklessTopBar("Set up your ledger", onBack = {}, marker = "1 of 3")
        Overline("Less typing. More living.")
        PageHeadline("You pay.\nIt's already logged.", lead = "Inkless reads bank payment alerts, so expenses from any UPI app find their way here.")
        TintCard {
            Text("Safe to spend today", style = MaterialTheme.typography.titleMedium, color = Inkless.colors.primary)
            Text("₹84", style = Inkless.type.moneyHero)
            BudgetBar(spentPaise = 2_70_000, budgetPaise = 12_00_000)
        }
        FilterPills(listOf("All", "Food & Canteen", "Food Delivery"), selected = 0, onSelect = {})
        DayHeader("Today · Fri, 6 Nov", total = "₹300")
        LedgerRow("Sharma Canteen", "₹90", meta = "Food & Canteen · 13:10", leading = { CategoryTile("food_canteen") })
        LedgerRow(
            "Blinkit",
            "₹420",
            meta = "Groceries & Essentials · UPI",
            leading = { CategoryTile("groceries") },
            chip = { StatusChip("+3 more items") },
        )
        LedgerRow("Swiggy refund", "+₹120", meta = "Food Delivery · Category offset", amountColor = Inkless.colors.credit, leading = { CategoryTile("food_delivery", isCredit = true) })
        InklessCard {
            InfoRow("Date", "5 Nov 2026 · 18:26")
            InfoRow("Source", "Bank SMS + PhonePe")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            StatusChip("Allowed")
            StatusChip("Blocked", tone = ChipTone.Check)
            StatusChip("Optional", tone = ChipTone.Neutral)
            StatusChip("Over", tone = ChipTone.Danger)
        }
        PillChoice(listOf("1 month", "3 months", "6 months"), selected = 0, onSelect = {})
        InklessTextField("hostel_ledger", {}, label = "Username", note = "Available · letters, numbers and underscores", noteKind = FieldNote.Good, trailingIcon = R.drawable.icon_circle_check)
        InklessTextField("₹12,000", {}, label = "Monthly allowance", money = true)
        ToggleRow("Cloud backup", checked = true, onCheckedChange = {}, subtitle = "Only parsed ledger data. Raw SMS stays on this phone.")
        WarmCard {
            Text("Verify your email when you can", style = MaterialTheme.typography.titleSmall)
            Text("You can keep using Inkless.", style = MaterialTheme.typography.bodySmall)
        }
        PrivacyNote("Raw alert text stays on your phone.", body = "OTPs, promotions and personal messages are ignored.")
        SectionHeader("November category budgets", action = "Manage", onAction = {})
        InklessCard(padding = PaddingValues(0.dp)) {
            NavRow("Capture health", onClick = {}, subtitle = "SMS active · 2 things to check", icon = R.drawable.icon_activity)
            NavRow("Delete account", onClick = {}, destructive = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            InitialTile("hostel_ledger")
            IconTile(R.drawable.icon_scan_line)
        }
        PrimaryButton("Create account", onClick = {}, icon = R.drawable.icon_arrow_right)
        SecondaryButton("Continue with Google", onClick = {})
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextLink("Log in", onClick = {})
            TextLink("Try the demo →", onClick = {})
        }
        EmptyState("All sorted.", "New payments Inkless can't place by itself land here.")
        Footnote("No account needed for the demo.\nNo ads. No analytics. Just your money, made clear.")
    }
}

@Preview(name = "Kit · light", widthDp = 412, heightDp = 2400)
@Composable
private fun KitLightPreview() {
    InklessTheme(darkTheme = false) { KitGallery() }
}

@Preview(name = "Kit · dark", widthDp = 412, heightDp = 2400)
@Composable
private fun KitDarkPreview() {
    InklessTheme(darkTheme = true) { KitGallery() }
}
