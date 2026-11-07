package com.mehul.inkless.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mehul.inkless.R
import com.mehul.inkless.budget.BudgetSnapshot
import com.mehul.inkless.budget.BudgetText
import com.mehul.inkless.ui.budget.PaceChart
import com.mehul.inkless.ui.budget.SpendRing
import com.mehul.inkless.ui.components.CategoryTile
import com.mehul.inkless.ui.format.Money
import com.mehul.inkless.ui.theme.Inkless
import com.mehul.inkless.ui.theme.LocalInklessColors
import com.mehul.inkless.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

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
        onPreviousMonth = viewModel::previousMonth,
        onNextMonth = viewModel::nextMonth,
        onOpenSettings = onOpenSettings,
        onResendVerification = viewModel::resendVerification,
        onOpenTransaction = onOpenTransaction,
        onOpenSmsSetup = onOpenSmsSetup,
        onAddCash = { addingCash = true },
        toSort = toSort,
        onOpenReview = onOpenReview,
        budget = budget,
        onOpenBudget = onOpenBudget,
        onOpenCategory = onOpenCategory,
        onOpenAsk = onOpenAsk,
    )

    if (addingCash) {
        QuickAddSheet(
            categories = categories,
            onAdd = { amount, categoryId, note ->
                viewModel.addCash(amount, categoryId, note)
                addingCash = false
            },
            onDismiss = { addingCash = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeContent(
    state: HomeUiState,
    account: HomeAccount,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onOpenSettings: () -> Unit,
    onResendVerification: () -> Unit,
    onOpenTransaction: (String) -> Unit,
    onOpenSmsSetup: () -> Unit,
    onAddCash: () -> Unit,
    toSort: Int,
    onOpenReview: () -> Unit,
    budget: HomeBudget,
    onOpenBudget: () -> Unit,
    onOpenCategory: (String) -> Unit,
    onOpenAsk: () -> Unit,
) {
    Scaffold(
        floatingActionButton = {
            if (state is HomeUiState.Ready) {
                ExtendedFloatingActionButton(
                    onClick = onAddCash,
                    icon = { Icon(painterResource(R.drawable.icon_plus), contentDescription = null) },
                    text = { Text("Cash") },
                )
            }
        },
        topBar = { HomeTopBar(isDemo = account.isDemo, onOpenAsk = onOpenAsk, onOpenSettings = onOpenSettings) },
    ) { padding ->
        when (state) {
            HomeUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
            is HomeUiState.Ready -> ReadyContent(
                state, padding, onPreviousMonth, onNextMonth, onOpenTransaction,
                // The budget is about now, so it's shown on the current month only.
                budget = budget.takeIf { !state.canGoForward } ?: HomeBudget.Hidden,
                onOpenBudget = onOpenBudget,
                onOpenCategory = onOpenCategory,
            ) {
                account.unverifiedEmail?.let { email ->
                    VerifyEmailBanner(email, account.verificationSent, onResendVerification)
                }
                if (account.restoring) RestoringCard()
                if (account.offerSmsSetup) SmsSetupCard(onClick = onOpenSmsSetup)
                if (toSort > 0) ToSortCard(count = toSort, onClick = onOpenReview)
            }
        }
    }
}

@Composable
private fun ReadyContent(
    state: HomeUiState.Ready,
    padding: PaddingValues,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onOpenTransaction: (String) -> Unit,
    budget: HomeBudget,
    onOpenBudget: () -> Unit,
    onOpenCategory: (String) -> Unit,
    banner: @Composable () -> Unit,
) {
    val pagerState = rememberPagerState { state.tabs.size }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
        banner()
        MonthHeader(
            month = state.month,
            isCurrentMonth = !state.canGoForward,
            spentPaise = state.spentPaise,
            onPrevious = onPreviousMonth,
            onNext = onNextMonth,
        )
        PrimaryScrollableTabRow(selectedTabIndex = pagerState.currentPage, edgePadding = 12.dp) {
            state.tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(tab.title, maxLines = 1) },
                )
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { state.tabs[it].key },
        ) { page ->
            CategoryPage(
                tab = state.tabs[page],
                today = state.today,
                bottomPadding = padding.calculateBottomPadding(),
                onOpenTransaction = onOpenTransaction,
                budget = budget,
                onOpenBudget = onOpenBudget,
                onOpenCategory = onOpenCategory,
            )
        }
    }
}

@Composable
private fun MonthHeader(
    month: YearMonth,
    isCurrentMonth: Boolean,
    spentPaise: Long,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = "Previous month")
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = month.format(MONTH_FORMAT),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = Money.format(spentPaise),
                style = Inkless.type.moneyMedium,
            )
            Text(
                text = if (isCurrentMonth) "spent so far this month" else "spent that month",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onNext, enabled = !isCurrentMonth) {
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = "Next month")
        }
    }
}

@Composable
private fun CategoryPage(
    tab: CategoryTab,
    today: LocalDate,
    bottomPadding: Dp,
    onOpenTransaction: (String) -> Unit,
    budget: HomeBudget,
    onOpenBudget: () -> Unit,
    onOpenCategory: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // Room at the bottom so the Cash button never covers the last row.
        contentPadding = PaddingValues(bottom = bottomPadding + 88.dp),
    ) {
        // The month header already shows the overall total, so only categories get a summary row.
        if (tab.key != HomeStateBuilder.ALL_TAB_KEY) {
            item(key = "summary") { CategorySummary(tab, onClick = { onOpenCategory(tab.key) }) }
        } else {
            // Always there, even while empty: the budget loads a moment after the list, and a row
            // appearing above the first visible one would land out of sight, above the top.
            item(key = "budget") {
                when (budget) {
                    is HomeBudget.Shown -> BudgetCard(budget.snapshot, onClick = onOpenBudget)
                    HomeBudget.NotSet -> SetBudgetCard(onClick = onOpenBudget)
                    HomeBudget.Hidden -> Unit
                }
            }
        }

        if (tab.days.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = "Nothing here this month.",
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        tab.days.forEach { day ->
            item(key = "day-${day.date}") { DayHeader(day, today) }
            items(day.rows, key = { it.id }) { row -> TransactionItem(row, onClick = { onOpenTransaction(row.id) }) }
        }
    }
}

@Composable
private fun CategorySummary(tab: CategoryTab, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryTile(tab.key, size = 48.dp)
        Column(Modifier.weight(1f)) {
            Text(tab.title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = if (tab.count == 1) "1 payment" else "${tab.count} payments",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = Money.format(tab.totalPaise),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = "Details", tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Safe to spend today, the month's ring and how long the money lasts at this pace. */
@Composable
private fun BudgetCard(snapshot: BudgetSnapshot, onClick: () -> Unit) {
    val over = snapshot.safeToSpendPaise < 0
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SpendRing(fraction = snapshot.usedFraction, size = 72.dp) {
                    Text("${(snapshot.usedFraction * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        BudgetText.safeToSpendAmount(snapshot),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(BudgetText.safeToSpendLabel(snapshot), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        BudgetText.spentOfBudget(snapshot),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            PaceChart(
                cumulativePaise = snapshot.cumulativePaise,
                budgetPaise = snapshot.budgetPaise,
                cycleDays = snapshot.cycle.days,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(BudgetText.runway(snapshot), style = MaterialTheme.typography.bodyMedium)
            BudgetText.food(snapshot)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SetBudgetCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Set your monthly allowance", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Then Inkless tells you what you can spend today, and when the money runs out at this pace.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null)
        }
    }
}

@Composable
private fun DayHeader(day: DayGroup, today: LocalDate) {
    val label = when (day.date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> day.date.format(DAY_FORMAT)
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (day.spentPaise > 0) {
            Text(
                text = Money.format(day.spentPaise),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TransactionItem(row: TransactionRow, onClick: () -> Unit) {
    val zone = remember { ZoneId.systemDefault() }
    val time = Instant.ofEpochMilli(row.timestamp).atZone(zone).format(TIME_FORMAT)
    val amountColor = if (row.isCredit) LocalInklessColors.current.credit else MaterialTheme.colorScheme.onSurface

    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.title, modifier = Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (row.moreItems > 0) MoreItemsBadge(row.moreItems)
            }
        },
        supportingContent = {
            Text(
                text = listOfNotNull(
                    time,
                    "split · yours ${Money.format(row.myPaise)}".takeIf { row.friendsSharePaise > 0 },
                    row.vpa,
                ).joinToString("  ·  "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = { CategoryTile(row.categoryId, isCredit = row.isCredit, isTransfer = row.isTransfer) },
        trailingContent = {
            Text(
                text = (if (row.isCredit) "+" else "−") + Money.format(row.amountPaise),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = amountColor,
            )
        },
    )
}

/** "+4 more": the rest of a broken-down payment's items, counted in their own categories. */
@Composable
private fun MoreItemsBadge(count: Int) {
    Surface(
        modifier = Modifier.padding(start = 8.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            "+$count more",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}


@Composable
private fun VerifyEmailBanner(email: String, sent: Boolean, onResend: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(R.drawable.ic_mail),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Verify your email",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    if (sent) "Sent again to $email" else "We sent a link to $email",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onResend, enabled = !sent) { Text("Resend") }
        }
    }
}

@Composable
private fun ToSortCard(count: Int, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (count == 1) "1 payment to sort" else "$count payments to sort",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text("Sort", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun RestoringCard() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Restoring your backup…", style = MaterialTheme.typography.titleSmall)
            Text(
                "Your payments, categories and rules are on their way from the cloud.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SmsSetupCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(R.drawable.ic_sms),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Turn on auto-capture",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    "Let Inkless read your bank's payment SMS so payments appear by themselves.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

/** "inkless" with the ledger it shows under it, Ask on the left of the two icons and Settings on the right. */
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
                Text(
                    "DEMO LEDGER · Saved on this phone",
                    style = MaterialTheme.typography.labelSmall,
                    color = Inkless.colors.muted,
                )
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

private val MONTH_FORMAT = DateTimeFormatter.ofPattern("MMMM yyyy")
private val DAY_FORMAT = DateTimeFormatter.ofPattern("EEE, d MMM")
private val TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a")
