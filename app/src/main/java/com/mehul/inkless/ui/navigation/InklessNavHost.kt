package com.mehul.inkless.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import com.mehul.inkless.ui.accounts.MyAccountsScreen
import com.mehul.inkless.ui.ask.AskScreen
import com.mehul.inkless.ui.breakdown.BreakdownScreen
import com.mehul.inkless.ui.budget.BudgetScreen
import com.mehul.inkless.ui.category.CategoryScreen
import com.mehul.inkless.ui.data.DataScreen
import com.mehul.inkless.ui.friends.FriendScreen
import com.mehul.inkless.ui.friends.FriendsScreen
import com.mehul.inkless.ui.capture.SmsSetupScreen
import com.mehul.inkless.ui.capture.UnreadScreen
import com.mehul.inkless.ui.health.CaptureHealthScreen
import com.mehul.inkless.ui.home.HomeScreen
import com.mehul.inkless.ui.insights.InsightsScreen
import com.mehul.inkless.ui.onboarding.OnboardingScreen
import com.mehul.inkless.ui.review.ReviewScreen
import com.mehul.inkless.ui.rules.RulesScreen
import com.mehul.inkless.ui.settings.SettingsScreen
import com.mehul.inkless.ui.split.SplitScreen
import com.mehul.inkless.ui.transaction.TransactionScreen
import com.mehul.inkless.ui.wrapped.WrappedScreen
import kotlinx.serialization.Serializable

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

@Composable
fun InklessNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreen(
                onOpenSettings = { navController.navigate(SettingsRoute) },
                onOpenTransaction = { navController.navigate(TransactionRoute(it)) },
                onOpenSmsSetup = { navController.navigate(SmsSetupRoute) },
                onOpenReview = { navController.navigate(ReviewRoute) },
                onOpenBudget = { navController.navigate(BudgetRoute) },
                onOpenCategory = { navController.navigate(CategoryRoute(it)) },
                onOpenFriends = { navController.navigate(FriendsRoute) },
                onOpenInsights = { navController.navigate(InsightsRoute) },
                onOpenOnboarding = { navController.navigate(OnboardingRoute) },
            )
        }
        composable<OnboardingRoute> {
            OnboardingScreen(
                onDone = { navController.popBackStack() },
                onOpenSmsSetup = { navController.navigate(SmsSetupRoute) },
                onOpenHealth = { navController.navigate(CaptureHealthRoute) },
                onOpenBudget = { navController.navigate(BudgetRoute) },
                onOpenMyAccounts = { navController.navigate(MyAccountsRoute) },
            )
        }
        // The spending alert notification opens inkless://insights.
        composable<InsightsRoute>(deepLinks = listOf(navDeepLink<InsightsRoute>(basePath = "inkless://insights"))) {
            InsightsScreen(
                onBack = { navController.popBackStack() },
                onAsk = { navController.navigate(AskRoute) },
                onWrapped = { navController.navigate(WrappedRoute(it.toString())) },
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
        composable<FriendsRoute> {
            FriendsScreen(
                onBack = { navController.popBackStack() },
                onOpenFriend = { navController.navigate(FriendRoute(it)) },
            )
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
        composable<ReviewRoute> {
            ReviewScreen(
                onBack = { navController.popBackStack() },
                onOpenTransaction = { navController.navigate(TransactionRoute(it)) },
            )
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
        composable<TransactionRoute>(deepLinks = listOf(navDeepLink<TransactionRoute>(basePath = "inkless://transaction"))) {
            TransactionScreen(
                onBack = { navController.popBackStack() },
                onBreakDown = { navController.navigate(BreakdownRoute(it)) },
                onSplit = { navController.navigate(SplitRoute(it)) },
            )
        }
        // From the payment, the "break it down?" notifications (inkless://breakdown/<id>) and Share to Inkless.
        composable<BreakdownRoute>(deepLinks = listOf(navDeepLink<BreakdownRoute>(basePath = "inkless://breakdown"))) {
            BreakdownScreen(onDone = { navController.popBackStack() })
        }
        composable<SmsSetupRoute> {
            SmsSetupScreen(onDone = { navController.popBackStack() })
        }
        composable<UnreadRoute> {
            UnreadScreen(onBack = { navController.popBackStack() })
        }
    }
}
