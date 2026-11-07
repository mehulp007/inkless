package com.mehul.inkless.ui.home

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import com.mehul.inkless.data.db.dao.MerchantSpend
import com.mehul.inkless.data.db.entity.CaptureSource
import com.mehul.inkless.data.seed.CategoryIds
import com.mehul.inkless.ui.category.CategoryContent
import com.mehul.inkless.ui.category.CategoryDetail
import com.mehul.inkless.ui.category.CategoryStop
import com.mehul.inkless.ui.theme.Inkless
import com.mehul.inkless.ui.theme.InklessTheme
import com.mehul.inkless.ui.theme.Spacing
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Home, the cash sheet and a category's page, with fixed data instead of their view models. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w412dp-h2400dp-xxhdpi")
class HomeScreensTest {

    @get:Rule
    val compose = createComposeRule()

    private var opened: String? = null
    private var category: String? = null
    private var cashAsked = false

    @Composable
    private fun Home(budget: HomeBudget = HomeBudget.Shown(HomeFixtures.snapshot), account: HomeAccount = HomeAccount(isDemo = true)) {
        HomeContent(
            state = HomeFixtures.state, account = account, budget = budget, toSort = 2,
            onPreviousMonth = {}, onNextMonth = {}, onOpenSettings = {}, onOpenAsk = {}, onResendVerification = {},
            onOpenTransaction = { opened = it }, onOpenSmsSetup = {}, onOpenReview = {}, onOpenBudget = {},
            onOpenCategory = { category = it }, onAddCash = { cashAsked = true },
        )
    }

    private fun show(dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent { InklessTheme(darkTheme = dark) { content() } }
    }

    @Test
    fun homeLight() {
        show { Home() }
        compose.onRoot().captureRoboImage()
    }

    @Test
    fun homeDark() {
        show(dark = true) { Home() }
        compose.onRoot().captureRoboImage()
    }

    @Test
    fun homeWithoutABudgetAndAnEmailToVerify() {
        show { Home(budget = HomeBudget.NotSet, account = HomeAccount(unverifiedEmail = "student@example.com")) }
        compose.onNodeWithText("Set your monthly allowance").assertExists()
        compose.onNodeWithText("Verify your email").assertExists()
        compose.onRoot().captureRoboImage()
    }

    @Test
    fun homeTellsTheFigmaStory() {
        show { Home() }
        compose.onNodeWithText("₹84").assertExists()
        compose.onNodeWithText("₹300 spent today").assertExists()
        compose.onNodeWithText("₹2,700 / ₹12,000").assertExists()
        compose.onNodeWithText("Today · Fri, 6 Nov").assertExists()
        compose.onNodeWithText("₹820 net").assertExists()
        compose.onNodeWithText("+4 more items", substring = true).assertDoesNotExist()
        compose.onNodeWithText("+3 more items").assertExists()
        compose.onNodeWithText("2–4 Nov · 3 payments").assertExists()
        compose.onNodeWithText("+₹12,000").assertExists()
    }

    @Test
    fun aPillFiltersTheListAndLeadsToTheCategory() {
        show { Home() }
        compose.onNode(hasText("Travel") and isSelectable()).performScrollTo().performClick()
        compose.onNode(hasText("Travel") and isSelectable()).assertIsSelected()
        compose.onNodeWithText("Rapido").assertExists()
        compose.onNodeWithText("Sharma Canteen").assertDoesNotExist()
        compose.onNodeWithText("2 payments").performClick()
        assertThat(category).isEqualTo(CategoryIds.TRAVEL)
    }

    @Test
    fun earlierOpensUp() {
        show { Home() }
        compose.onNodeWithText("Night Mess").assertDoesNotExist()
        compose.onNodeWithText("2–4 Nov · 3 payments").performScrollTo().performClick()
        compose.onNodeWithText("Night Mess").assertExists()
    }

    @Test
    fun aPaymentOpensAndCashIsAsked() {
        show { Home() }
        compose.onNodeWithText("Sharma Canteen").performClick()
        assertThat(opened).isEqualTo("s")
        compose.onNodeWithText("Add cash").performScrollTo().performClick()
        assertThat(cashAsked).isTrue()
    }

    @Test
    fun cashSheet() {
        var saved: List<Any?>? = null
        show {
            Box(Modifier.background(Inkless.colors.background).padding(Spacing.gutter)) {
                QuickAddForm(
                    HomeFixtures.categories, HomeFixtures.snapshot,
                    onAdd = { a, c, m, n -> saved = listOf(a, c, m, n) }, onClose = {},
                    today = HomeFixtures.today, focusAmount = false,
                )
            }
        }
        val save = compose.onNode(hasText("Save cash expense") and hasClickAction())
        save.assertIsNotEnabled()
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("40")
        compose.onAllNodes(hasSetTextAction())[1].performTextInput("Block C Chai")
        compose.onNodeWithText("Safe today after saving: ₹44").assertExists()
        compose.onRoot().captureRoboImage()
        save.assertIsEnabled().performClick()
        assertThat(saved).containsExactly(4_000L, CategoryIds.FOOD_CANTEEN, "Block C Chai", null).inOrder()
    }

    @Test
    fun cashSheetPicksACategory() {
        var saved: List<Any?>? = null
        show {
            QuickAddForm(HomeFixtures.categories, null, onAdd = { a, c, m, n -> saved = listOf(a, c, m, n) }, onClose = {}, today = HomeFixtures.today, focusAmount = false)
        }
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("120")
        compose.onNodeWithText("Food & Canteen").performClick()
        compose.onNodeWithText("Travel").performClick()
        compose.onNode(hasText("Save cash expense") and hasClickAction()).performClick()
        assertThat(saved).containsExactly(12_000L, CategoryIds.TRAVEL, null, null).inOrder()
    }

    @Test
    fun categoryPage() {
        val canteen = HomeFixtures.categories.single { it.id == CategoryIds.FOOD_CANTEEN }.copy(monthlyBudgetPaise = 300_000)
        val month = YearMonth.of(2026, 11)
        val detail = CategoryDetail(
            loading = false,
            category = canteen,
            months = listOf(210_000L, 192_000L, 234_000L, 218_000L, 246_000L, 78_000L).mapIndexed { i, p -> month.minusMonths(5L - i) to p },
            topMerchants = listOf(
                MerchantSpend("m1", "Sharma Canteen", 53_000, 3),
                MerchantSpend("m2", "Night Mess", 22_000, 1),
                MerchantSpend("m3", "Block C Chai", 3_000, 1),
            ),
            today = listOf(
                CategoryStop("s", "Sharma Canteen", HomeFixtures.transactions.single { it.id == "s" }.timestamp, 9_000, false, setOf(CaptureSource.SMS, CaptureSource.NOTIFICATION)),
                CategoryStop("c", "Block C Chai", HomeFixtures.transactions.single { it.id == "c" }.timestamp, 3_000, false, setOf(CaptureSource.SMS)),
            ),
        )
        show { CategoryContent(detail, month, onBack = {}, onSetBudget = {}, onOpenTransaction = { opened = it }) }
        // The big total, and the same number over November's bar.
        assertThat(compose.onAllNodesWithText("₹780").fetchSemanticsNodes()).hasSize(2)
        compose.onNodeWithText("26% used").assertExists()
        compose.onNodeWithText("13:10 · Bank SMS + notification").assertExists()
        compose.onRoot().captureRoboImage()
        compose.onNodeWithText("13:10 · Bank SMS + notification").performClick()
        assertThat(opened).isEqualTo("s")
    }
}
