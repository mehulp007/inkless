package com.mehul.inkless.ui.home

import com.mehul.inkless.ui.theme.cappedTextSize
import com.mehul.inkless.ui.theme.Motion
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mehul.inkless.R
import com.mehul.inkless.budget.BudgetSnapshot
import com.mehul.inkless.budget.BudgetText
import com.mehul.inkless.ui.budget.SpendRing
import com.mehul.inkless.ui.components.CategoryTile
import com.mehul.inkless.ui.components.DayHeader
import com.mehul.inkless.ui.components.EmptyState
import com.mehul.inkless.ui.components.FilterPills
import com.mehul.inkless.ui.components.Footnote
import com.mehul.inkless.ui.components.IconTile
import com.mehul.inkless.ui.components.InklessBottomSheet
import com.mehul.inkless.ui.components.InklessCard
import com.mehul.inkless.ui.components.LedgerRow
import com.mehul.inkless.ui.components.PrimaryButton
import com.mehul.inkless.ui.components.StatusChip
import com.mehul.inkless.ui.components.TextLink
import com.mehul.inkless.ui.components.TintCard
import com.mehul.inkless.ui.components.WarmCard
import com.mehul.inkless.ui.format.Money
import com.mehul.inkless.ui.theme.Inkless
import com.mehul.inkless.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenTransaction: (String) -> Unit,
    onOpenSmsSetup: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenBudget: () -> Unit,
    onOpenCategory: (String) -> Unit,
    onOpenAsk: () -> Unit,
    onOpenOnboarding: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val budget by viewModel.budget.collectAsStateWithLifecycle()
    val quickAddRequested by viewModel.quickAddRequested.collectAsStateWithLifecycle()
    val toSort by viewModel.toSort.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val account by viewModel.account.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    var addingCash by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(quickAddRequested) {
        if (quickAddRequested) {
            addingCash = true
            viewModel.quickAddShown()
        }
    }

    LaunchedEffect(account.showOnboarding) {
        if (account.showOnboarding) {
            viewModel.onboardingShown()
            onOpenOnboarding()
        }
    }

    // Coming back from the email app is the moment the verification link was likely clicked.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshAccount()
        onPauseOrDispose {}
    }

    HomeContent(
        state = state,
        account = account,
        budget = budget,
        toSort = toSort,
        onPreviousMonth = viewModel::previousMonth,
        onNextMonth = viewModel::nextMonth,
        onOpenSettings = onOpenSettings,
        onOpenAsk = onOpenAsk,
        onResendVerification = viewModel::resendVerification,
        onOpenTransaction = onOpenTransaction,
        onOpenSmsSetup = onOpenSmsSetup,
        onOpenReview = onOpenReview,
        onOpenBudget = onOpenBudget,
        onOpenCategory = onOpenCategory,
        onAddCash = { addingCash = true },
    )

    if (addingCash) {
        QuickAddSheet(
            categories = categories,
            snapshot = (budget as? HomeBudget.Shown)?.snapshot,
            onAdd = { amount, categoryId, merchant, note ->
                viewModel.addCash(amount, categoryId, merchant, note)
                addingCash = false
            },
            onDismiss = { addingCash = false },
        )
    }
}

/** Home without its view model: everything it shows comes in, everything it does goes out. */
@Composable
fun HomeContent(
    state: HomeUiState,
    account: HomeAccount,
    budget: HomeBudget,
    toSort: Int,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAsk: () -> Unit,
    onResendVerification: () -> Unit,
    onOpenTransaction: (String) -> Unit,
    onOpenSmsSetup: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenBudget: () -> Unit,
    onOpenCategory: (String) -> Unit,
    onAddCash: () -> Unit,
) {
    Scaffold(
        containerColor = Inkless.colors.background,
        topBar = { HomeTopBar(isDemo = account.isDemo, onOpenAsk = onOpenAsk, onOpenSettings = onOpenSettings) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        when (state) {
            HomeUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator(color = Inkless.colors.primary)
            }
            is HomeUiState.Ready -> Ledger(
                state = state,
                account = account,
                // The budget is about now, so it's shown on the current month only.
                budget = budget.takeIf { !state.canGoForward } ?: HomeBudget.Hidden,
                toSort = toSort,
                padding = padding,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
                onResendVerification = onResendVerification,
                onOpenTransaction = onOpenTransaction,
                onOpenSmsSetup = onOpenSmsSetup,
                onOpenReview = onOpenReview,
                onOpenBudget = onOpenBudget,
                onOpenCategory = onOpenCategory,
                onAddCash = onAddCash,
            )
        }
    }
}

@Composable
private fun Ledger(
    state: HomeUiState.Ready,
    account: HomeAccount,
    budget: HomeBudget,
    toSort: Int,
    padding: PaddingValues,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onResendVerification: () -> Unit,
    onOpenTransaction: (String) -> Unit,
    onOpenSmsSetup: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenBudget: () -> Unit,
    onOpenCategory: (String) -> Unit,
    onAddCash: () -> Unit,
) {
    var selectedKey by rememberSaveable { mutableStateOf(HomeStateBuilder.ALL_TAB_KEY) }
    var earlierOpen by rememberSaveable { mutableStateOf(false) }
    var explaining by rememberSaveable { mutableStateOf(false) }
    val selected = state.tabs.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)
    val tab = state.tabs[selected]
    val select = { index: Int -> selectedKey = state.tabs[index.coerceIn(0, state.tabs.lastIndex)].key }
    val swipe = with(LocalDensity.current) { 72.dp.toPx() }
    val zone = ZoneId.systemDefault()

    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .consumeWindowInsets(padding)
            // A sideways swipe on the list moves to the next or previous pill, as the old tabs did.
            .pointerInput(state.tabs.size, selected) {
                var dragged = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragged = 0f },
                    onDragEnd = {
                        if (abs(dragged) > swipe) select(if (dragged < 0) selected + 1 else selected - 1)
                    },
                ) { _, amount -> dragged += amount }
            },
        contentPadding = PaddingValues(top = Spacing.s, bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        account.unverifiedEmail?.let { email ->
            item(key = "verify") { Gutter { VerifyEmailCard(email, account.verificationSent, onResendVerification) } }
        }
        if (account.restoring) item(key = "restoring") { Gutter { RestoringCard() } }
        if (account.offerSmsSetup) item(key = "sms") { Gutter { SmsSetupCard(onClick = onOpenSmsSetup) } }

        // Always there, even while empty: the budget loads a moment after the list, and a card
        // appearing above the first visible row would land out of sight.
        item(key = "budget") {
            when (budget) {
                is HomeBudget.Shown -> Gutter {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
                        SafeToSpendCard(budget.snapshot, onExplain = { explaining = true }, onClick = onOpenBudget)
                        PaceNote(budget.snapshot)
                    }
                }
                HomeBudget.NotSet -> Gutter { SetBudgetCard(onClick = onOpenBudget) }
                HomeBudget.Hidden -> Unit
            }
        }

        item(key = "month") { MonthSwitcher(state, onPreviousMonth, onNextMonth) }
        item(key = "pills") {
            FilterPills(
                labels = state.tabs.map { it.title },
                selected = selected,
                onSelect = select,
                edgePadding = Spacing.gutter,
            )
        }
        if (tab.key != HomeStateBuilder.ALL_TAB_KEY) {
            item(key = "summary") { Gutter { CategorySummary(tab, onClick = { onOpenCategory(tab.key) }) } }
        }
        if (toSort > 0) item(key = "to-sort") { Gutter { ToSortCard(count = toSort, onClick = onOpenReview) } }

        if (tab.days.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    title = "Nothing here yet",
                    body = if (state.canGoForward) "No payments that month." else "Payments show up here as they happen.",
                    icon = R.drawable.icon_receipt,
                )
            }
        }

        tab.recent.forEach { day -> dayGroup(day, state.today, zone, onOpenTransaction) }

        tab.earlier?.let { earlier ->
            item(key = "earlier-header") { Gutter { DayHeader("Earlier this month") } }
            if (earlier.paymentCount > 0) {
                item(key = "earlier") {
                    Gutter {
                        LedgerRow(
                            title = "${dateRange(earlier)} · ${plural(earlier.paymentCount, "payment")}",
                            amount = Money.format(earlier.spentPaise),
                            meta = categorySummary(earlier),
                            leading = { IconTile(if (earlierOpen) R.drawable.icon_chevron_down else R.drawable.icon_history) },
                            onClick = { earlierOpen = !earlierOpen },
                        )
                    }
                }
                if (earlierOpen) earlier.days.forEach { day -> dayGroup(day, state.today, zone, onOpenTransaction) }
            }
            items(earlier.income, key = { "income-${it.id}" }) { row ->
                Gutter { Payment(row, zone, onOpenTransaction, withDate = true) }
            }
        }

        if (!state.canGoForward) {
            item(key = "cash") { Gutter { PrimaryButton("Add cash", onClick = onAddCash, icon = R.drawable.icon_plus) } }
        }
        item(key = "footnote") {
            Gutter { Footnote("Refunds reduce category spend. Transfers between your own accounts don't count as spending.") }
        }
    }

    if (explaining && budget is HomeBudget.Shown) {
        SafeToSpendSheet(budget.snapshot, onDismiss = { explaining = false })
    }
}

private fun LazyListScope.dayGroup(day: DayGroup, today: LocalDate, zone: ZoneId, onOpen: (String) -> Unit) {
    item(key = "day-${day.date}") {
        Gutter {
            DayHeader(
                label = dayLabel(day.date, today),
                total = when {
                    day.net -> "${Money.format(day.spentPaise)} net"
                    day.spentPaise > 0 -> Money.format(day.spentPaise)
                    else -> null
                },
            )
        }
    }
    items(day.rows, key = { "${day.date}-${it.id}" }) { row -> Gutter { Payment(row, zone, onOpen) } }
}

@Composable
private fun Payment(row: TransactionRow, zone: ZoneId, onOpen: (String) -> Unit, withDate: Boolean = false) {
    val at = Instant.ofEpochMilli(row.timestamp).atZone(zone)
    val moneyIn = row.isCredit && !row.isTransfer
    LedgerRow(
        title = row.title,
        amount = if (moneyIn) "+${Money.format(row.amountPaise)}" else Money.format(row.amountPaise),
        meta = paymentMeta(row, if (withDate) at.format(DAY_MONTH) else at.format(TIME)),
        amountColor = if (moneyIn) Inkless.colors.credit else Inkless.colors.ink,
        leading = { CategoryTile(row.categoryId, isCredit = row.isCredit, isTransfer = row.isTransfer) },
        chip = if (row.moreItems > 0) {
            { StatusChip(if (row.moreItems == 1) "+1 more item" else "+${row.moreItems} more items") }
        } else {
            null
        },
        onClick = { onOpen(row.id) },
    )
}

/** "Food & Canteen · 13:10 · To sort", "Own-account transfer · Excluded", "1 Nov · Income · Not spending" */
internal fun paymentMeta(row: TransactionRow, time: String): String = when {
    row.isTransfer -> "Own-account transfer · Excluded"
    row.isIncome -> "$time · Income · Not spending"
    row.isRefund -> "${row.categoryName ?: "Other"} · Category offset"
    else -> listOfNotNull(
        row.categoryName ?: "Other",
        time,
        "To sort".takeIf { row.toSort },
        "Your part ${Money.format(row.myPaise)}".takeIf { row.friendsSharePaise > 0 },
    ).joinToString(" · ")
}

/** "2–4 Nov", or "30 Oct – 2 Nov" across a month's end. */
internal fun dateRange(group: EarlierGroup): String = when {
    group.from == group.to -> group.from.format(DAY_MONTH)
    group.from.month == group.to.month -> "${group.from.dayOfMonth}–${group.to.format(DAY_MONTH)}"
    else -> "${group.from.format(DAY_MONTH)} – ${group.to.format(DAY_MONTH)}"
}

/** "Food, recharge & more" from the biggest categories, each by the first word of its name. */
internal fun categorySummary(group: EarlierGroup): String {
    val names = group.topCategories
        .map { it.substringBefore(" &") }
        .mapIndexed { i, name -> if (i == 0) name else name.lowercase() }
    return when {
        names.isEmpty() -> "Refunds"
        group.moreCategories -> names.joinToString(", ") + " & more"
        names.size == 1 -> names.single()
        else -> names.dropLast(1).joinToString(", ") + " & " + names.last()
    }
}

internal fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today · ${date.format(WEEKDAY_DAY_MONTH)}"
    today.minusDays(1) -> "Yesterday · ${date.format(DAY_MONTH)}"
    else -> date.format(WEEKDAY_DAY_MONTH)
}

private fun plural(count: Int, word: String) = if (count == 1) "1 $word" else "$count ${word}s"

@Composable
private fun Gutter(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter)) { content() }
}

/** The green card at the top: what can still be spent today, and the month around it. */
@Composable
private fun SafeToSpendCard(snapshot: BudgetSnapshot, onExplain: () -> Unit, onClick: () -> Unit) {
    val colors = Inkless.colors
    val over = snapshot.safeToSpendPaise < 0
    TintCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (over) "Over today's share" else "Safe to spend today",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = if (over) colors.danger else colors.primary,
            )
            IconButton(onClick = onExplain, modifier = Modifier.size(32.dp)) {
                Icon(painterResource(R.drawable.icon_info), contentDescription = "How this is worked out", tint = colors.primary, modifier = Modifier.size(20.dp))
            }
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            // A new payment rolls the number over instead of swapping it in place.
            AnimatedContent(
                targetState = BudgetText.safeToSpendAmount(snapshot),
                transitionSpec = {
                    (fadeIn(tween(Motion.MEDIUM)) + slideInVertically(tween(Motion.MEDIUM)) { it / 3 }) togetherWith
                        (fadeOut(tween(Motion.SHORT)) + slideOutVertically(tween(Motion.MEDIUM)) { -it / 3 }) using
                        SizeTransform(clip = false)
                },
                label = "safe to spend",
            ) { amount ->
                Text(amount, style = Inkless.type.moneyHero, color = if (over) colors.danger else colors.ink)
            }
            Text(
                BudgetText.spentToday(snapshot),
                modifier = Modifier.padding(bottom = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted,
            )
        }
        HorizontalDivider(Modifier.padding(vertical = Spacing.l), color = colors.tintStrong)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
            SpendRing(fraction = snapshot.usedFraction, size = 64.dp) {
                val label = MaterialTheme.typography.labelSmall
                Text(
                    BudgetText.usedPercent(snapshot),
                    style = label.copy(fontSize = cappedTextSize(label.fontSize)),
                    color = colors.ink,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(BudgetText.spentSlashBudget(snapshot), style = MaterialTheme.typography.titleMedium, color = colors.ink)
                Text(BudgetText.cycleLine(snapshot), style = MaterialTheme.typography.bodySmall, color = colors.muted)
                Text(
                    BudgetText.leftLine(snapshot),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (snapshot.remainingPaise < 0) colors.danger else colors.primary,
                )
            }
        }
    }
}

/** How long the money lasts at this pace, and the daily food cap if there is one. */
@Composable
private fun PaceNote(snapshot: BudgetSnapshot) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Icon(painterResource(R.drawable.icon_calendar_clock), contentDescription = null, tint = Inkless.colors.primary, modifier = Modifier.size(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            listOfNotNull(BudgetText.paceLine(snapshot), BudgetText.onTrackLine(snapshot), BudgetText.food(snapshot)).forEach {
                Text(it, style = MaterialTheme.typography.bodySmall, color = Inkless.colors.muted)
            }
        }
    }
}

/** "How today's ₹84 is worked out": the sum behind the big number. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SafeToSpendSheet(snapshot: BudgetSnapshot, onDismiss: () -> Unit) {
    val leftThisMorning = snapshot.budgetPaise - (snapshot.spentPaise - snapshot.spentTodayPaise)
    val daysLeft = snapshot.cycle.daysLeft(snapshot.today)
    InklessBottomSheet(onDismiss = onDismiss) {
        Text(
            "How today's ${BudgetText.safeToSpendAmount(snapshot)} is worked out",
            style = MaterialTheme.typography.titleLarge,
            color = Inkless.colors.ink,
        )
        TintCard {
            SumLine("Left this morning", Money.format(leftThisMorning))
            SumLine("Days left, today included", "÷ $daysLeft")
            HorizontalDivider(Modifier.padding(vertical = Spacing.s), color = Inkless.colors.tintStrong)
            SumLine("Today's share", Money.format(snapshot.todaysSharePaise))
            SumLine("Spent today", "− ${Money.format(snapshot.spentTodayPaise.coerceAtLeast(0))}")
            HorizontalDivider(Modifier.padding(vertical = Spacing.s), color = Inkless.colors.tintStrong)
            SumLine(
                BudgetText.safeToSpendLabel(snapshot).replaceFirstChar { it.uppercase() },
                Money.format(snapshot.safeToSpendPaise),
                strong = true,
            )
        }
        Text(
            "Spend less today and tomorrow's share grows; spend more and it shrinks. Refunds count back, and transfers between your own accounts don't count at all.",
            style = MaterialTheme.typography.bodyMedium,
            color = Inkless.colors.muted,
        )
    }
}

@Composable
private fun SumLine(label: String, value: String, strong: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
        Text(
            label,
            Modifier.weight(1f),
            style = if (strong) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            color = Inkless.colors.ink,
        )
        Text(value, style = if (strong) Inkless.type.moneyRow else MaterialTheme.typography.bodyMedium, color = Inkless.colors.ink)
    }
}

@Composable
private fun MonthSwitcher(state: HomeUiState.Ready, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) {
            Icon(painterResource(R.drawable.icon_chevron_left), contentDescription = "Previous month", tint = Inkless.colors.ink)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(state.month.format(MONTH), style = MaterialTheme.typography.titleMedium, color = Inkless.colors.ink)
            Text(
                "${Money.format(state.spentPaise)} spent" + if (state.canGoForward) "" else " so far",
                style = MaterialTheme.typography.bodySmall,
                color = Inkless.colors.muted,
            )
        }
        IconButton(onClick = onNext, enabled = state.canGoForward) {
            Icon(
                painterResource(R.drawable.icon_chevron_right),
                contentDescription = "Next month",
                tint = if (state.canGoForward) Inkless.colors.ink else Inkless.colors.hairline,
            )
        }
    }
}

/** With a category pill picked: its total, and the way to its own page. */
@Composable
private fun CategorySummary(tab: CategoryTab, onClick: () -> Unit) {
    InklessCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            CategoryTile(tab.key)
            Column(Modifier.weight(1f)) {
                Text(tab.title, style = MaterialTheme.typography.titleMedium, color = Inkless.colors.ink)
                Text(plural(tab.count, "payment"), style = MaterialTheme.typography.bodySmall, color = Inkless.colors.muted)
            }
            Text(Money.format(tab.totalPaise), style = Inkless.type.moneyRow, color = Inkless.colors.ink)
            Icon(
                painterResource(R.drawable.icon_chevron_right),
                contentDescription = "Open ${tab.title}",
                tint = Inkless.colors.muted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ToSortCard(count: Int, onClick: () -> Unit) {
    InklessCard(onClick = onClick, padding = PaddingValues(horizontal = Spacing.l, vertical = Spacing.m)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Icon(painterResource(R.drawable.icon_inbox), contentDescription = null, tint = Inkless.colors.primary, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f)) {
                Text(plural(count, "payment") + " to sort", style = MaterialTheme.typography.titleSmall, color = Inkless.colors.ink)
                Text("One choice now. Remembered next time.", style = MaterialTheme.typography.bodySmall, color = Inkless.colors.muted)
            }
            Icon(painterResource(R.drawable.icon_arrow_right), contentDescription = null, tint = Inkless.colors.primary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SetBudgetCard(onClick: () -> Unit) {
    TintCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text("Set your monthly allowance", style = MaterialTheme.typography.titleMedium, color = Inkless.colors.ink)
                Text(
                    "Then Home says what's safe to spend today, and when the money runs out at this pace.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Inkless.colors.muted,
                )
            }
            Icon(painterResource(R.drawable.icon_arrow_right), contentDescription = null, tint = Inkless.colors.primary)
        }
    }
}

@Composable
private fun VerifyEmailCard(email: String, sent: Boolean, onResend: () -> Unit) {
    WarmCard(padding = PaddingValues(start = Spacing.l, end = Spacing.l, top = Spacing.s, bottom = Spacing.s)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Icon(painterResource(R.drawable.icon_mail), contentDescription = null, tint = Inkless.colors.onWarm, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f)) {
                Text("Verify your email", style = MaterialTheme.typography.titleSmall, color = Inkless.colors.ink)
                Text(
                    if (sent) "Sent again to $email" else "We sent a link to $email",
                    style = MaterialTheme.typography.bodySmall,
                    color = Inkless.colors.onWarm,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextLink("Resend", onClick = onResend, enabled = !sent, color = Inkless.colors.onWarm)
        }
    }
}

@Composable
private fun RestoringCard() {
    InklessCard {
        Text("Restoring your backup…", style = MaterialTheme.typography.titleSmall, color = Inkless.colors.ink)
        Text(
            "Your payments, categories and rules are on their way from the cloud.",
            style = MaterialTheme.typography.bodySmall,
            color = Inkless.colors.muted,
        )
        LinearProgressIndicator(
            Modifier.fillMaxWidth().padding(top = Spacing.m),
            color = Inkless.colors.primary,
            trackColor = Inkless.colors.tintStrong,
        )
    }
}

@Composable
private fun SmsSetupCard(onClick: () -> Unit) {
    TintCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Icon(painterResource(R.drawable.icon_message_square), contentDescription = null, tint = Inkless.colors.primary, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f)) {
                Text("Turn on auto-capture", style = MaterialTheme.typography.titleSmall, color = Inkless.colors.ink)
                Text(
                    "Let Inkless read your bank's payment SMS so payments appear by themselves.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Inkless.colors.muted,
                )
            }
            Icon(painterResource(R.drawable.icon_arrow_right), contentDescription = null, tint = Inkless.colors.primary, modifier = Modifier.size(20.dp))
        }
    }
}

/** "inkless" with the ledger it shows under it; Ask and Settings on the right. */
@Composable
private fun HomeTopBar(isDemo: Boolean, onOpenAsk: () -> Unit, onOpenSettings: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = Spacing.gutter, end = Spacing.s, top = Spacing.m, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("inkless", style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp), color = Inkless.colors.ink)
            if (isDemo) {
                Text("DEMO LEDGER · Saved on this phone", style = MaterialTheme.typography.labelSmall, color = Inkless.colors.muted)
            }
        }
        IconButton(onClick = onOpenAsk) {
            Icon(painterResource(R.drawable.icon_search), contentDescription = "Ask Inkless", tint = Inkless.colors.ink)
        }
        IconButton(onClick = onOpenSettings) {
            Icon(painterResource(R.drawable.icon_sliders), contentDescription = "Settings", tint = Inkless.colors.ink)
        }
    }
}

private val MONTH = DateTimeFormatter.ofPattern("MMMM yyyy")
private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM")
private val WEEKDAY_DAY_MONTH = DateTimeFormatter.ofPattern("EEE, d MMM")
private val TIME = DateTimeFormatter.ofPattern("HH:mm")
