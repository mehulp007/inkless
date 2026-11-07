package com.mehul.inkless.ui.money

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import com.mehul.inkless.ask.Answer
import com.mehul.inkless.ask.AnswerLine
import com.mehul.inkless.data.db.dao.PaymentRow
import com.mehul.inkless.data.db.entity.RecurringEntity
import com.mehul.inkless.data.seed.CategoryIds
import com.mehul.inkless.insights.AccuracyReport
import com.mehul.inkless.insights.AlertPeriod
import com.mehul.inkless.insights.SpendAlert
import com.mehul.inkless.insights.WrappedStats
import com.mehul.inkless.ml.WeekAccuracy
import com.mehul.inkless.ui.ask.AskContent
import com.mehul.inkless.ui.ask.Exchange
import com.mehul.inkless.ui.budget.CategoryBudgetsContent
import com.mehul.inkless.ui.home.HomeFixtures
import com.mehul.inkless.ui.insights.InsightsContent
import com.mehul.inkless.ui.insights.InsightsState
import com.mehul.inkless.ui.theme.InklessTheme
import com.mehul.inkless.ui.wrapped.WrappedContent
import com.mehul.inkless.ui.wrapped.WrappedState
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Money tab and the screens it leads to, with fixed data instead of their view models. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w412dp-h2000dp-xxhdpi")
class MoneyScreensTest {

    @get:Rule
    val compose = createComposeRule()

    private val categories = HomeFixtures.categories
    private fun category(id: String) = categories.single { it.id == id }
    private val november = YearMonth.of(2026, 11)
    private fun at(day: Int) = LocalDate.of(2026, 11, day).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    // The Figma month: ₹2,700 spent by the 6th, at ₹450 a day lately.
    private val snapshot = HomeFixtures.snapshot.copy(cumulativePaise = listOf(30_000L, 70_000L, 1_10_000L, 1_80_000L, 2_40_000L, 2_70_000L), dailyFoodCapPaise = 20_000)

    private val budgets = listOf(
        CategoryBudget(category(CategoryIds.FOOD_CANTEEN).copy(monthlyBudgetPaise = 3_00_000), 78_000),
        CategoryBudget(category(CategoryIds.FOOD_DELIVERY).copy(monthlyBudgetPaise = 1_50_000), 42_000),
        CategoryBudget(category(CategoryIds.GROCERIES).copy(monthlyBudgetPaise = 2_00_000), 45_000),
    )

    private fun show(dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent { InklessTheme(darkTheme = dark) { content() } }
    }

    @Composable
    private fun Money(state: MoneyState, onWrapped: (YearMonth) -> Unit = {}) {
        MoneyContent(
            state, weeklySummary = true, onWeeklySummary = {}, onOpenBudget = {}, onOpenCategoryBudgets = {},
            onOpenCategory = {}, onOpenInsights = {}, onOpenAsk = {}, onOpenWrapped = onWrapped,
        )
    }

    private val moneyState = MoneyState(
        loading = false, snapshot = snapshot, month = november, budgets = budgets,
        semesterName = "Jul–Dec 2026", semesterPaise = 38_42_000, wrappedMonth = november.minusMonths(1),
    )

    @Test
    fun moneyTab() {
        var wrapped: YearMonth? = null
        show { Money(moneyState, onWrapped = { wrapped = it }) }
        compose.onNodeWithText("₹9,300").assertExists()
        compose.onNodeWithText("left of your ₹12,000 allowance · 22.5% used").assertExists()
        compose.onNodeWithText("₹9,600 ÷ 25 days − ₹300 = ₹84").assertExists()
        compose.onNodeWithText("₹780 / ₹3,000").assertExists()
        compose.onRoot().captureRoboImage()
        compose.onNodeWithText("October, wrapped").performClick()
        assertThat(wrapped).isEqualTo(november.minusMonths(1))
    }

    @Test
    fun moneyTabDark() {
        show(dark = true) { Money(moneyState) }
        compose.onRoot().captureRoboImage()
    }

    @Test
    fun moneyTabWithoutAnAllowance() {
        show { Money(moneyState.copy(snapshot = null, budgets = emptyList())) }
        compose.onNodeWithText("Set your monthly allowance").assertExists()
        compose.onNodeWithText("No category budgets yet. Give a category a monthly amount and its bar shows up here.").assertExists()
    }

    @Test
    fun categoryBudgets() {
        val all = categories.map { c -> budgets.firstOrNull { it.category.id == c.id } ?: CategoryBudget(c, 0) }
        show { CategoryBudgetsContent(all, onBack = {}, onSetBudget = { _, _ -> }) }
        compose.onNodeWithText("₹780 this month").assertExists()
        compose.onRoot().captureRoboImage()
    }

    @Test
    fun insights() {
        val state = InsightsState(
            loading = false,
            accuracy = AccuracyReport(
                weeks = (0 until 12).map { WeekAccuracy(LocalDate.of(2026, 8, 17).plusWeeks(it.toLong()), if (it < 2) 0 else 10, if (it < 2) 0 else 7 + it % 4) },
                guesses = 100, right = 85, recentRight = 29, recentGuesses = 30, filed = 120, overridden = 6, learnedFrom = 213,
            ),
            alerts = listOf(SpendAlert(CategoryIds.FOOD_DELIVERY, AlertPeriod.WEEK, 1_24_000, 54_000, "w45")),
            recurring = listOf(
                RecurringEntity("m1", "Hostel Laundry", CategoryIds.LAUNDRY, 14_000, 7, 6, at(2), at(9)),
                RecurringEntity("m2", "Jio Prepaid", CategoryIds.RECHARGE, 34_900, 30, 4, at(3), LocalDate.of(2026, 12, 3).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()),
            ),
            categories = categories.associateBy { it.id },
        )
        show { InsightsContent(state, onBack = {}, onOpenCategory = {}, now = at(6)) }
        compose.onNodeWithText("Food Delivery: ₹1,240 this week").assertExists()
        compose.onNodeWithText("97% right lately").assertExists()
        compose.onRoot().captureRoboImage()
    }

    @Test
    fun ask() {
        val exchanges = listOf(
            Exchange(
                "How much on food this month?",
                Answer(
                    "₹645 on Food & Canteen and Food Delivery this month, over 3 payments.",
                    listOf(AnswerLine("Swiggy", "5 Nov", 38_000, "t1"), AnswerLine("Swiggy", "2 Nov", 22_500, "t2"), AnswerLine("Sharma Canteen", "3 Nov", 4_000, "t3")),
                    headlinePaise = 64_500,
                    understood = listOf("Total", "Food & Canteen", "Food Delivery", "This month"),
                ),
            ),
        )
        var opened: String? = null
        show { AskContent(exchanges, onBack = {}, onAsk = {}, onOpenTransaction = { opened = it }) }
        compose.onNodeWithText("₹645").assertExists()
        compose.onNodeWithText("UNDERSTOOD AS").assertExists()
        compose.onRoot().captureRoboImage()
        compose.onNodeWithText("Sharma Canteen").performClick()
        assertThat(opened).isEqualTo("t3")
    }

    @Test
    fun askBeforeAnyQuestion() {
        var asked: String? = null
        show { AskContent(emptyList(), onBack = {}, onAsk = { asked = it }, onOpenTransaction = {}) }
        compose.onNodeWithText("Kitna kharcha kal?").performClick()
        assertThat(asked).isEqualTo("Kitna kharcha kal?")
    }

    @Test
    fun wrapped() {
        val october = YearMonth.of(2026, 10)
        val stats = WrappedStats(
            month = october, totalPaise = 11_58_000, previousTotalPaise = 10_34_000, payments = 86,
            topCategoryId = CategoryIds.FOOD_CANTEEN, topCategoryPaise = 3_90_000,
            topPlace = "Sharma Canteen", topPlacePaise = 1_85_000, mostVisited = "Block C Chai", mostVisitedTimes = 14,
            biggest = PaymentRow("b", at(1), 2_40_000, CategoryIds.SHOPPING, null, "Amazon", null),
            busiestWeekday = DayOfWeek.SATURDAY, noSpendDays = 3, lateNight = 7, small = 31,
        )
        val state = WrappedState(october, stats, categories.associateBy { it.id }, canGoForward = true)
        show { WrappedContent(state, onBack = {}, onPrevious = {}, onNext = {}, onShare = { _, _ -> }) }
        compose.onNodeWithText("October, in a few numbers.").assertExists()
        compose.onNodeWithText("11% more than September").assertExists()
        compose.onRoot().captureRoboImage()
    }

    @Test
    fun wrappedWithNothingOpen() {
        show { WrappedContent(WrappedState(YearMonth.of(2026, 10), unavailable = true), onBack = {}, onPrevious = {}, onNext = {}, onShare = { _, _ -> }) }
        compose.onNodeWithText("Nothing to look back on").assertExists()
    }
}
