package com.mehul.inkless.ui.auth

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.google.common.truth.Truth.assertThat
import com.mehul.inkless.ui.theme.InklessTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The sign-in forms with their view models swapped for plain values. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w412dp-h1400dp-xxhdpi")
class AuthFormsTest {

    @get:Rule
    val compose = createComposeRule()

    private fun field(index: Int) = compose.onAllNodes(hasSetTextAction())[index]

    private fun button(text: String) = compose.onNode(hasText(text) and hasClickAction())

    @Test
    fun loginWaitsForBothFields() {
        var sent: Pair<String, String>? = null
        compose.setContent {
            InklessTheme(darkTheme = false) {
                LoginContent(FormStatus(), onBack = {}, onLogIn = { id, pw -> sent = id to pw }, onGoogle = {}, onForgotPassword = {}, onSignUp = {})
            }
        }
        button("Log in").assertIsNotEnabled()
        field(0).performTextInput("hostel_ledger")
        button("Log in").assertIsNotEnabled()
        field(1).performTextInput("chai-and-maggi-9")
        button("Log in").assertIsEnabled().performClick()
        assertThat(sent).isEqualTo("hostel_ledger" to "chai-and-maggi-9")
    }

    @Test
    fun loginShowsTheError() {
        compose.setContent {
            InklessTheme(darkTheme = false) {
                LoginContent(FormStatus(error = "Wrong password"), onBack = {}, onLogIn = { _, _ -> }, onGoogle = {}, onForgotPassword = {}, onSignUp = {})
            }
        }
        compose.onNodeWithText("Wrong password").assertExists()
    }

    @Test
    fun forgotPasswordPassesTheNameOn() {
        var asked: String? = null
        compose.setContent {
            InklessTheme(darkTheme = false) {
                LoginContent(FormStatus(), onBack = {}, onLogIn = { _, _ -> }, onGoogle = {}, onForgotPassword = { asked = it }, onSignUp = {})
            }
        }
        field(0).performTextInput("  hostel_ledger ")
        compose.onNodeWithText("Forgot password?").performClick()
        assertThat(asked).isEqualTo("hostel_ledger")
    }

    @Test
    fun signUpShowsEachUsernameState() {
        val states = listOf(
            UsernameStatus.Checking to "Checking…",
            UsernameStatus.Available to "Available · letters, numbers and underscores",
            UsernameStatus.Taken to "is taken",
            UsernameStatus.Unknown to "Couldn't check right now",
        )
        var status by mutableStateOf<UsernameStatus>(UsernameStatus.Empty)
        compose.setContent {
            InklessTheme(darkTheme = false) {
                SignUpContent(FormStatus(), status, onBack = {}, onLogIn = {}, onUsernameChange = {}, onEdit = {}, onSignUp = { _, _, _, _ -> })
            }
        }
        states.forEach { (state, note) ->
            status = state
            compose.onNode(hasText(note, substring = true)).assertExists()
        }
    }

    @Test
    fun signUpNeedsAFreeNameAndMatchingPasswords() {
        var status by mutableStateOf<UsernameStatus>(UsernameStatus.Taken)
        compose.setContent {
            InklessTheme(darkTheme = false) {
                SignUpContent(FormStatus(), status, onBack = {}, onLogIn = {}, onUsernameChange = {}, onEdit = {}, onSignUp = { _, _, _, _ -> })
            }
        }
        field(0).performTextInput("chai_wala")
        field(1).performTextInput("chai@example.com")
        field(2).performTextInput("masala-chai-42")
        field(3).performTextInput("masala-chai-41")
        button("Create account").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Passwords don't match").assertExists()

        status = UsernameStatus.Available
        button("Create account").assertIsNotEnabled()
        field(3).performTextInput("2")
        // "masala-chai-412" still isn't the same.
        button("Create account").assertIsNotEnabled()
    }

    @Test
    fun signUpSendsTheBackupChoice() {
        var sent: List<Any>? = null
        compose.setContent {
            InklessTheme(darkTheme = false) {
                SignUpContent(FormStatus(), UsernameStatus.Available, onBack = {}, onLogIn = {}, onUsernameChange = {}, onEdit = {}, onSignUp = { u, e, p, b -> sent = listOf(u, e, p, b) })
            }
        }
        field(0).performTextInput("chai_wala")
        field(1).performTextInput("chai@example.com")
        field(2).performTextInput("masala-chai-42")
        field(3).performTextInput("masala-chai-42")
        compose.onNodeWithText("Cloud backup").performScrollTo().performClick()
        button("Create account").performScrollTo().assertIsEnabled().performClick()
        assertThat(sent).containsExactly("chai_wala", "chai@example.com", "masala-chai-42", false).inOrder()
    }

    @Test
    fun forgotPasswordWaitsBeforeResending() {
        var cooldown by mutableStateOf(0)
        var status by mutableStateOf(FormStatus())
        var sends = 0
        compose.setContent {
            InklessTheme(darkTheme = false) {
                ForgotPasswordContent("hostel_ledger", status, cooldown, onBack = {}, onSend = { sends++ }, onEdit = {})
            }
        }
        button("Send reset link").assertIsEnabled().performClick()
        assertThat(sends).isEqualTo(1)

        status = FormStatus(done = true)
        cooldown = 59
        compose.onNode(hasText("If an account exists", substring = true)).assertExists()
        button("Resend in 0:59").assertIsNotEnabled()

        cooldown = 0
        button("Resend link").assertIsEnabled()
    }
}
