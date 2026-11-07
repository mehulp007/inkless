package com.mehul.inkless.ui.auth

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.mehul.inkless.lock.LockedScreen
import com.mehul.inkless.ui.theme.InklessTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Welcome, the sign-in screens and the lock screen as they look with nothing typed yet. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class AuthScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun capture(dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent { InklessTheme(darkTheme = dark) { content() } }
        compose.onRoot().captureRoboImage()
    }

    @Test
    fun welcome() = capture { WelcomeContent(busy = false, error = null, onSignUp = {}, onGoogle = {}, onLogIn = {}, onDemo = {}) }

    @Test
    fun welcomeDark() = capture(dark = true) { WelcomeContent(busy = false, error = null, onSignUp = {}, onGoogle = {}, onLogIn = {}, onDemo = {}) }

    @Test
    fun logIn() = capture { LoginContent(FormStatus(), onBack = {}, onLogIn = { _, _ -> }, onGoogle = {}, onForgotPassword = {}, onSignUp = {}) }

    @Test
    fun signUp() = capture {
        SignUpContent(FormStatus(), UsernameStatus.Empty, onBack = {}, onLogIn = {}, onUsernameChange = {}, onEdit = {}, onSignUp = { _, _, _, _ -> })
    }

    @Test
    fun forgotPasswordSent() = capture {
        ForgotPasswordContent("hostel_ledger", FormStatus(done = true), resendIn = 59, onBack = {}, onSend = {}, onEdit = {})
    }

    @Test
    fun chooseUsername() = capture {
        ChooseUsernameContent("student@example.com", FormStatus(), UsernameStatus.Empty, onUsernameChange = {}, onClaim = {}, onLogOut = {})
    }

    @Test
    fun locked() = capture { LockedScreen(problem = null, onUnlock = {}) }

    @Test
    fun lockedDarkAfterACancel() = capture(dark = true) { LockedScreen(problem = "Authentication cancelled", onUnlock = {}) }
}
