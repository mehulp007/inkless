package com.mehul.inkless.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import com.mehul.inkless.ui.accounts.MyAccountsScreen
import com.mehul.inkless.ui.ask.AskScreen
import com.mehul.inkless.ui.breakdown.BreakdownScreen
import com.mehul.inkless.ui.budget.BudgetScreen
import com.mehul.inkless.ui.capture.SmsSetupScreen
import com.mehul.inkless.ui.capture.UnreadScreen
import com.mehul.inkless.ui.category.CategoryScreen
import com.mehul.inkless.ui.components.BottomTab
import com.mehul.inkless.ui.components.InklessBottomBar
import com.mehul.inkless.ui.data.DataScreen
import com.mehul.inkless.ui.friends.FriendScreen
import com.mehul.inkless.ui.friends.FriendsScreen
import com.mehul.inkless.ui.health.CaptureHealthScreen
import com.mehul.inkless.ui.home.HomeScreen
import com.mehul.inkless.ui.insights.InsightsScreen
import com.mehul.inkless.ui.money.MoneyScreen
import com.mehul.inkless.ui.onboarding.OnboardingScreen
import com.mehul.inkless.ui.review.ReviewScreen
import com.mehul.inkless.ui.rules.RulesScreen
import com.mehul.inkless.ui.settings.SettingsScreen
import com.mehul.inkless.ui.split.SplitScreen
import com.mehul.inkless.ui.theme.Inkless
import com.mehul.inkless.ui.transaction.TransactionScreen
import com.mehul.inkless.ui.wrapped.WrappedScreen
import java.time.YearMonth
import kotlinx.serialization.Serializable

/** The addresses notifications and Share to Inkless open the app with. */
object DeepLinks {
    const val INSIGHTS = "inkless://insights"
    const val TRANSACTION = "inkless://transaction"
    const val BREAKDOWN = "inkless://breakdown"
}

@Serializable
object HomeRoute

@Serializable
object SettingsRoute

@Serializable
data class TransactionRoute(val id: String)

/** [draft] is a breakdown read from a shared order, waiting in ShareDrafts. */
@Serializable
data class BreakdownRoute(val transactionId: String, val draft: String? = null)

@Serializable
object BudgetRoute

@Serializable
data class CategoryRoute(val categoryId: String)

@Serializable
object FriendsRoute

@Serializable
data class FriendRoute(val friendId: String)

@Serializable
data class SplitRoute(val transactionId: String)

@Serializable
object DataRoute

@Serializable
object OnboardingRoute

@Serializable
object InsightsRoute

@Serializable
object AskRoute

/** [month] as "2026-10". */
@Serializable
data class WrappedRoute(val month: String)

@Serializable
object SmsSetupRoute

@Serializable
object UnreadRoute

@Serializable
object ReviewRoute

@Serializable
object RulesRoute

@Serializable
object MyAccountsRoute

@Serializable
object CaptureHealthRoute

@Serializable
object MoneyRoute

@Composable
fun InklessNavHost(viewModel: ShellViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val toSort by viewModel.toSort.collectAsStateWithLifecycle()
    val quickAdd by viewModel.quickAddRequested.collectAsStateWithLifecycle()
    InklessShell(navController, toSortCount = toSort, quickAddRequested = quickAdd) { inklessGraph(navController) }
}

/**
 * The frame around every signed-in screen: the bottom bar on each tab's first screen (hidden
 * everywhere else) and the screens themselves, from [graph].
 */
@Composable
fun InklessShell(
    navController: NavHostController,
    toSortCount: Int,
    quickAddRequested: Boolean = false,
    graph: NavGraphBuilder.() -> Unit,
) {
    val entry by navController.currentBackStackEntryAsState()
    val rootTab = Tab.atRoot(entry?.destination)

    // The widget's Cash button: back to Home's first screen, which opens the quick-add sheet.
    LaunchedEffect(quickAddRequested) {
        if (quickAddRequested) navController.popBackStack<HomeRoute>(inclusive = false)
    }

    Scaffold(
        containerColor = Inkless.colors.background,
        // Each screen handles the status bar itself; the bottom bar handles the navigation bar.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (rootTab != null) {
                InklessBottomBar(
                    tabs = Tab.entries.map { BottomTab(it.label, it.icon, count = if (it == Tab.Sort) toSortCount else 0) },
                    selectedIndex = rootTab.ordinal,
                    onSelect = { index ->
                        val tab = Tab.entries[index]
                        if (tab != rootTab) navController.selectTab(tab)
                    },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = HomeTab,
            // Consumed, so screens above the bar don't pad for the navigation bar a second time.
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            builder = graph,
        )
    }
}

/**
 * Every signed-in screen. A tab's first screen lives in that tab's graph; everything else opens
 * on top of whichever tab you're in.
 */
fun NavGraphBuilder.inklessGraph(navController: NavHostController) {
    navigation<HomeTab>(startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreen(
                onOpenSettings = { navController.navigate(SettingsRoute) },
                onOpenTransaction = { navController.navigate(TransactionRoute(it)) },
                onOpenSmsSetup = { navController.navigate(SmsSetupRoute) },
                onOpenReview = { navController.selectTab(Tab.Sort) },
                onOpenBudget = { navController.navigate(BudgetRoute) },
                onOpenCategory = { navController.navigate(CategoryRoute(it)) },
                onOpenAsk = { navController.navigate(AskRoute) },
                onOpenOnboarding = { navController.navigate(OnboardingRoute) },
            )
        }
    }
    navigation<SortTab>(startDestination = ReviewRoute) {
        composable<ReviewRoute> {
            ReviewScreen(onOpenTransaction = { navController.navigate(TransactionRoute(it)) })
        }
    }
    navigation<FriendsTab>(startDestination = FriendsRoute) {
        composable<FriendsRoute> {
            FriendsScreen(onOpenFriend = { navController.navigate(FriendRoute(it)) })
        }
    }
    navigation<MoneyTab>(startDestination = MoneyRoute) {
        composable<MoneyRoute> {
            MoneyScreen(
                onOpenBudget = { navController.navigate(BudgetRoute) },
                onOpenInsights = { navController.navigate(InsightsRoute) },
                onOpenAsk = { navController.navigate(AskRoute) },
                onOpenWrapped = { navController.navigate(WrappedRoute(YearMonth.now().minusMonths(1).toString())) },
            )
        }
        // The spending alert notification opens inkless://insights, with the Money tab under it.
        composable<InsightsRoute>(deepLinks = listOf(navDeepLink<InsightsRoute>(basePath = DeepLinks.INSIGHTS))) {
            InsightsScreen(
                onBack = { navController.popBackStack() },
                onAsk = { navController.navigate(AskRoute) },
                onWrapped = { navController.navigate(WrappedRoute(it.toString())) },
            )
        }
    }

    composable<OnboardingRoute> {
        OnboardingScreen(
            onDone = { navController.popBackStack() },
            onOpenHealth = { navController.navigate(CaptureHealthRoute) },
            onOpenMyAccounts = { navController.navigate(MyAccountsRoute) },
        )
    }
    composable<AskRoute> {
        AskScreen(
            onBack = { navController.popBackStack() },
            onOpenTransaction = { navController.navigate(TransactionRoute(it)) },
        )
    }
    composable<WrappedRoute> {
        WrappedScreen(onBack = { navController.popBackStack() })
    }
    composable<FriendRoute> {
        FriendScreen(
            onBack = { navController.popBackStack() },
            onOpenTransaction = { navController.navigate(TransactionRoute(it)) },
        )
    }
    composable<SplitRoute> {
        SplitScreen(onDone = { navController.popBackStack() })
    }
    composable<BudgetRoute> {
        BudgetScreen(onBack = { navController.popBackStack() })
    }
    composable<CategoryRoute> {
        CategoryScreen(onBack = { navController.popBackStack() })
    }
    composable<SettingsRoute> {
        SettingsScreen(
            onBack = { navController.popBackStack() },
            onOpenSmsSetup = { navController.navigate(SmsSetupRoute) },
            onOpenUnread = { navController.navigate(UnreadRoute) },
            onOpenRules = { navController.navigate(RulesRoute) },
            onOpenMyAccounts = { navController.navigate(MyAccountsRoute) },
            onOpenHealth = { navController.navigate(CaptureHealthRoute) },
            onOpenBudget = { navController.navigate(BudgetRoute) },
            onOpenData = { navController.navigate(DataRoute) },
        )
    }
    composable<DataRoute> {
        DataScreen(onBack = { navController.popBackStack() })
    }
    composable<MyAccountsRoute> {
        MyAccountsScreen(onBack = { navController.popBackStack() })
    }
    composable<CaptureHealthRoute> {
        CaptureHealthScreen(
            onBack = { navController.popBackStack() },
            onOpenSmsSetup = { navController.navigate(SmsSetupRoute) },
        )
    }
    composable<RulesRoute> {
        RulesScreen(onBack = { navController.popBackStack() })
    }
    // The "what was this?" notification opens a payment through inkless://transaction/<id>.
    composable<TransactionRoute>(deepLinks = listOf(navDeepLink<TransactionRoute>(basePath = DeepLinks.TRANSACTION))) {
        TransactionScreen(
            onBack = { navController.popBackStack() },
            onBreakDown = { navController.navigate(BreakdownRoute(it)) },
            onSplit = { navController.navigate(SplitRoute(it)) },
        )
    }
    // From the payment, the "break it down?" notifications (inkless://breakdown/<id>) and Share to Inkless.
    composable<BreakdownRoute>(deepLinks = listOf(navDeepLink<BreakdownRoute>(basePath = DeepLinks.BREAKDOWN))) {
        BreakdownScreen(onDone = { navController.popBackStack() })
    }
    composable<SmsSetupRoute> {
        SmsSetupScreen(onDone = { navController.popBackStack() })
    }
    composable<UnreadRoute> {
        UnreadScreen(onBack = { navController.popBackStack() })
    }
}
