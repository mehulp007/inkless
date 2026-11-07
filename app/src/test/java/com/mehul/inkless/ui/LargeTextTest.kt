package com.mehul.inkless.ui

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import com.mehul.inkless.sync.BackupStatus
import com.mehul.inkless.ui.home.HomeAccount
import com.mehul.inkless.ui.home.HomeBudget
import com.mehul.inkless.ui.home.HomeContent
import com.mehul.inkless.ui.home.HomeFixtures
import com.mehul.inkless.ui.settings.LedgerSummary
import com.mehul.inkless.ui.settings.SettingsActions
import com.mehul.inkless.ui.settings.SettingsContent
import com.mehul.inkless.ui.settings.SettingsMode
import com.mehul.inkless.ui.settings.SettingsState
import com.mehul.inkless.ui.theme.InklessTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The busiest screens with the phone's text size at 200%: nothing should be cut off or overlap. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w412dp-h2400dp-xxhdpi")
class LargeTextTest {

    @get:Rule
    val compose = createComposeRule()

    private fun showLarge(content: @Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                InklessTheme(darkTheme = false) { content() }
            }
        }
    }

    @Test
    fun homeAtDoubleText() {
        showLarge {
            HomeContent(
                state = HomeFixtures.state, account = HomeAccount(isDemo = true), budget = HomeBudget.Shown(HomeFixtures.snapshot), toSort = 2,
                onPreviousMonth = {}, onNextMonth = {}, onOpenSettings = {}, onOpenAsk = {}, onResendVerification = {},
                onOpenTransaction = {}, onOpenSmsSetup = {}, onOpenReview = {}, onOpenBudget = {},
                onOpenCategory = {}, onAddCash = {},
            )
        }
        compose.onNodeWithText("₹84").assertExists()
        compose.onRoot().captureRoboImage()
    }

    @Test
    fun settingsAtDoubleText() {
        showLarge {
            SettingsContent(
                SettingsState(
                    mode = SettingsMode.Account("hostel_ledger", "student@example.com", emailVerified = false, hasPassword = true, hasGoogle = false),
                    backup = BackupStatus(on = true),
                    ledger = LedgerSummary("₹12,000 · arrives on the 1st", "13 categories · 2 rules", "SBI savings · me@okhdfc"),
                    version = "1.1.0",
                ),
                SettingsActions(),
            )
        }
        compose.onRoot().captureRoboImage()
    }
}
