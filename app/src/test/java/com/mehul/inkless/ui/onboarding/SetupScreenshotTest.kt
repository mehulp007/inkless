package com.mehul.inkless.ui.onboarding

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.mehul.inkless.budget.BudgetMath
import com.mehul.inkless.ui.budget.AllowanceForm
import com.mehul.inkless.ui.budget.BudgetForm
import com.mehul.inkless.ui.capture.CaptureSetupCards
import com.mehul.inkless.ui.capture.ImportHistorySection
import com.mehul.inkless.ui.capture.ImportState
import com.mehul.inkless.ui.theme.Inkless
import com.mehul.inkless.ui.theme.InklessTheme
import com.mehul.inkless.ui.theme.Spacing
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** What each first-run step shows, without the view models behind it. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w412dp-h1200dp-xxhdpi")
class SetupScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 7)

    private fun capture(dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent {
            InklessTheme(darkTheme = dark) {
                Column(
                    Modifier.fillMaxSize().background(Inkless.colors.background).padding(Spacing.gutter),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xl),
                ) { content() }
            }
        }
        compose.onRoot().captureRoboImage()
    }

    @Test
    fun captureNotAllowed() = capture { CaptureSetupCards(granted = false, onSmsAnswer = {}) }

    @Test
    fun captureAllowedDark() = capture(dark = true) { CaptureSetupCards(granted = true, onSmsAnswer = {}) }

    @Test
    fun allowance() = capture {
        AllowanceForm(
            form = BudgetForm(loading = false, amountText = "12000", dayText = "1", foodCapText = "200"),
            cycleFor = { BudgetMath.cycle(today, it) },
            onAmount = {},
            onDay = {},
            onFoodCap = {},
        )
    }

    @Test
    fun importChoosing() = capture {
        ImportHistorySection(ImportState.NotStarted, started = false, months = 1, onMonths = {}, today = today)
    }

    @Test
    fun importRunning() = capture {
        ImportHistorySection(ImportState.Running(done = 126, total = 180), started = true, months = 1, onMonths = {}, today = today)
    }

    @Test
    fun importFinished() = capture {
        ImportHistorySection(ImportState.Finished(saved = 38, unparsed = 3, scanned = 180), started = true, months = 1, onMonths = {}, today = today)
    }
}
