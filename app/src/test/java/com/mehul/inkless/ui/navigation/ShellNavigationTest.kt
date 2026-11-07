package com.mehul.inkless.ui.navigation

import android.app.Application
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.net.toUri
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import com.mehul.inkless.ui.theme.InklessTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The app shell with stand-in screens on the real routes, tabs and deep links: which tab is
 * selected, when the bar shows, and where each deep link lands.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w412dp-h900dp-xxhdpi")
class ShellNavigationTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var nav: NavHostController
    private var quickAdd by mutableStateOf(false)

    private fun launch(toSort: Int = 0) {
        compose.setContent {
            nav = rememberNavController()
            InklessTheme(darkTheme = false) {
                InklessShell(nav, toSortCount = toSort, quickAddRequested = quickAdd) { standIns(nav) }
            }
        }
    }

    private fun tab(label: String) = compose.onNodeWithText(label)

    @Test
    fun opensOnHomeWithTheBar() {
        launch()
        compose.onNodeWithText("Home screen, tapped 0").assertExists()
        tab("Home").assertIsSelected()
        listOf("To sort", "Friends", "Money").forEach { tab(it).assertExists() }
    }

    @Test
    fun toSortTabShowsTheCount() {
        launch(toSort = 3)
        tab("To sort · 3").assertExists()
    }

    @Test
    fun eachTabOpensItsFirstScreen() {
        launch()
        tab("Friends").performClick()
        compose.onNodeWithText("Friends screen").assertExists()
        tab("Friends").assertIsSelected()
        tab("Money").performClick()
        compose.onNodeWithText("Money screen").assertExists()
        tab("To sort").performClick()
        compose.onNodeWithText("To sort screen").assertExists()
        tab("Home").performClick()
        compose.onNodeWithText("Home screen, tapped 0").assertExists()
    }

    @Test
    fun aTabKeepsItsStateWhileAnotherIsOpen() {
        launch()
        compose.onNodeWithText("Tap").performClick()
        compose.onNodeWithText("Home screen, tapped 1").assertExists()
        tab("Friends").performClick()
        tab("Home").performClick()
        compose.onNodeWithText("Home screen, tapped 1").assertExists()
    }

    @Test
    fun barHidesOnScreensOpenedFromATab() {
        launch()
        tab("Friends").performClick()
        compose.onNodeWithText("Open friend").performClick()
        compose.onNodeWithText("Friend f1").assertExists()
        tab("Money").assertDoesNotExist()
        back()
        compose.onNodeWithText("Friends screen").assertExists()
        tab("Friends").assertIsSelected()
    }

    @Test
    fun backFromAnotherTabGoesHome() {
        launch()
        tab("Money").performClick()
        back()
        compose.onNodeWithText("Home screen, tapped 0").assertExists()
        tab("Home").assertIsSelected()
    }

    @Test
    fun paymentsToSortSwitchesTab() {
        launch()
        compose.onNodeWithText("Payments to sort").performClick()
        compose.onNodeWithText("To sort screen").assertExists()
        tab("To sort").assertIsSelected()
    }

    @Test
    fun insightsLinkLandsWithMoneyUnderIt() {
        launch()
        openLink(DeepLinks.INSIGHTS)
        compose.onNodeWithText("Insights screen").assertExists()
        back()
        compose.onNodeWithText("Money screen").assertExists()
        tab("Money").assertIsSelected()
        back()
        compose.onNodeWithText("Home screen, tapped 0").assertExists()
    }

    @Test
    fun paymentLinkLandsWithHomeUnderIt() {
        launch()
        openLink("${DeepLinks.TRANSACTION}/t9")
        compose.onNodeWithText("Payment t9").assertExists()
        back()
        compose.onNodeWithText("Home screen, tapped 0").assertExists()
    }

    @Test
    fun breakdownLinkKeepsTheDraft() {
        launch()
        openLink("${DeepLinks.BREAKDOWN}/t9?draft=d1")
        compose.onNodeWithText("Breakdown t9 from d1").assertExists()
    }

    @Test
    fun quickAddGoesBackToHome() {
        launch()
        tab("Friends").performClick()
        compose.onNodeWithText("Open friend").performClick()
        quickAdd = true
        compose.onNodeWithText("Home screen, tapped 0").assertExists()
        tab("Home").assertIsSelected()
    }

    private fun back() {
        compose.runOnIdle { nav.popBackStack() }
    }

    /** How notifications open the app: a fresh task built from the link. */
    private fun openLink(link: String) {
        compose.runOnIdle {
            nav.handleDeepLink(
                Intent(Intent.ACTION_VIEW, link.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            )
        }
    }
}

/** The real graph's shape, with a line of text standing in for each screen. */
private fun NavGraphBuilder.standIns(nav: NavHostController) {
    navigation<HomeTab>(startDestination = HomeRoute) {
        composable<HomeRoute> {
            var taps by rememberSaveable { mutableIntStateOf(0) }
            Column {
                Text("Home screen, tapped $taps")
                Button(onClick = { taps++ }) { Text("Tap") }
                Button(onClick = { nav.selectTab(Tab.Sort) }) { Text("Payments to sort") }
            }
        }
    }
    navigation<SortTab>(startDestination = ReviewRoute) {
        composable<ReviewRoute> { Text("To sort screen") }
    }
    navigation<FriendsTab>(startDestination = FriendsRoute) {
        composable<FriendsRoute> {
            Column {
                Text("Friends screen")
                Button(onClick = { nav.navigate(FriendRoute("f1")) }) { Text("Open friend") }
            }
        }
    }
    navigation<MoneyTab>(startDestination = MoneyRoute) {
        composable<MoneyRoute> { Text("Money screen") }
        composable<InsightsRoute>(deepLinks = listOf(navDeepLink<InsightsRoute>(basePath = DeepLinks.INSIGHTS))) {
            Text("Insights screen")
        }
    }
    composable<FriendRoute> { Text("Friend ${it.toRoute<FriendRoute>().friendId}") }
    composable<TransactionRoute>(deepLinks = listOf(navDeepLink<TransactionRoute>(basePath = DeepLinks.TRANSACTION))) {
        Text("Payment ${it.toRoute<TransactionRoute>().id}")
    }
    composable<BreakdownRoute>(deepLinks = listOf(navDeepLink<BreakdownRoute>(basePath = DeepLinks.BREAKDOWN))) {
        val route = it.toRoute<BreakdownRoute>()
        Text("Breakdown ${route.transactionId} from ${route.draft}")
    }
}
