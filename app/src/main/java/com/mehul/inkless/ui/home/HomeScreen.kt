package com.mehul.inkless.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mehul.inkless.R
import com.mehul.inkless.ui.format.Money
import com.mehul.inkless.ui.theme.LocalInklessColors
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(
        state = state,
        onPreviousMonth = viewModel::previousMonth,
        onNextMonth = viewModel::nextMonth,
        onOpenSettings = onOpenSettings,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeContent(
    state: HomeUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Inkless", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(8.dp))
                        DemoBadge()
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(painterResource(R.drawable.ic_settings), contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        when (state) {
            HomeUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
            is HomeUiState.Ready -> ReadyContent(state, padding, onPreviousMonth, onNextMonth)
        }
    }
}

@Composable
private fun ReadyContent(
    state: HomeUiState.Ready,
    padding: PaddingValues,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    val pagerState = rememberPagerState { state.tabs.size }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
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
                    text = { Text("${tab.emoji}  ${tab.title}", maxLines = 1) },
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
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
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
private fun CategoryPage(tab: CategoryTab, today: LocalDate, bottomPadding: Dp) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomPadding + 16.dp),
    ) {
        // The month header already shows the overall total, so only categories get a summary row.
        if (tab.key != HomeStateBuilder.ALL_TAB_KEY) {
            item(key = "summary") { CategorySummary(tab) }
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
            items(day.rows, key = { it.id }) { row -> TransactionItem(row) }
        }
    }
}

@Composable
private fun CategorySummary(tab: CategoryTab) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EmojiBadge(tab.emoji, Color(tab.colorArgb), size = 48.dp)
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
private fun TransactionItem(row: TransactionRow) {
    val zone = remember { ZoneId.systemDefault() }
    val time = Instant.ofEpochMilli(row.timestamp).atZone(zone).format(TIME_FORMAT)
    val amountColor = if (row.isCredit) LocalInklessColors.current.credit else MaterialTheme.colorScheme.onSurface

    ListItem(
        headlineContent = { Text(row.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                text = listOfNotNull(time, row.vpa).joinToString("  ·  "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = { EmojiBadge(row.emoji, Color(row.colorArgb)) },
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

@Composable
private fun EmojiBadge(emoji: String, color: Color, size: Dp = 40.dp) {
    Box(
        modifier = Modifier.size(size).background(color.copy(alpha = 0.18f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = (size.value * 0.48f).sp)
    }
}

@Composable
private fun DemoBadge() {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = "DEMO",
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

private val MONTH_FORMAT = DateTimeFormatter.ofPattern("MMMM yyyy")
private val DAY_FORMAT = DateTimeFormatter.ofPattern("EEE, d MMM")
private val TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a")
