package com.mehul.inkless.ui.settings

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import com.mehul.inkless.categorize.RuleField
import com.mehul.inkless.categorize.RuleMatch
import com.mehul.inkless.data.db.entity.AccountMatch
import com.mehul.inkless.data.db.entity.AccountRole
import com.mehul.inkless.data.db.entity.MyAccountEntity
import com.mehul.inkless.data.db.entity.RuleEntity
import com.mehul.inkless.data.seed.CategoryIds
import com.mehul.inkless.sync.BackupStatus
import com.mehul.inkless.ui.accounts.MyAccountsContent
import com.mehul.inkless.ui.data.DataContent
import com.mehul.inkless.ui.data.DataState
import com.mehul.inkless.ui.health.CaptureHealth
import com.mehul.inkless.ui.health.CaptureHealthContent
import com.mehul.inkless.ui.home.HomeFixtures
import com.mehul.inkless.ui.rules.RuleDraft
import com.mehul.inkless.ui.rules.RulesContent
import com.mehul.inkless.ui.theme.Appearance
import com.mehul.inkless.ui.theme.InklessTheme
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Settings and the pages behind it, with fixed data instead of their view models. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w412dp-h1900dp-xxhdpi")
class SettingsScreensTest {

    @get:Rule
    val compose = createComposeRule()

    private val zone = ZoneId.systemDefault()
    private fun at(day: Int, hour: Int, minute: Int) = LocalDateTime.of(2026, 11, day, hour, minute).atZone(zone).toInstant().toEpochMilli()
    private val now = at(6, 21, 0)

    private fun show(dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent { InklessTheme(darkTheme = dark) { content() } }
    }

    private val account = SettingsMode.Account(
        username = "hostel_ledger",
        email = "student@example.com",
        emailVerified = false,
        hasPassword = true,
        hasGoogle = false,
    )

    private val settings = SettingsState(
        mode = account,
        backup = BackupStatus(on = true, lastSyncedAt = at(6, 18, 30)),
        ledger = LedgerSummary(
            allowance = "₹12,000 · arrives on the 1st",
            categories = "13 categories · 2 rules",
            accounts = "SBI savings · me@okhdfc",
        ),
        health = CaptureHealth(sms = true, notificationAccess = false, questions = true, battery = false),
        unreadCount = 2,
        askAbovePaise = 10_000,
        version = "1.0.0",
        nowMillis = now,
    )

    @Test
    fun settings() {
        var picked: Appearance? = null
        var opened = ""
        show { SettingsContent(settings, SettingsActions(onAppearance = { picked = it }, onOpenHealth = { opened = "health" })) }
        compose.onNodeWithText("hostel_ledger").assertExists()
        compose.onNodeWithText("Verify your email when you can").assertExists()
        compose.onNodeWithText("Backed up 2 h ago").assertExists()
        compose.onNodeWithText("SMS active · 2 things to check").assertExists()
        compose.onRoot().captureRoboImage()
        compose.onNodeWithText("Dark").performScrollTo().performClick()
        assertThat(picked).isEqualTo(Appearance.DARK)
        compose.onNodeWithText("Capture health").performScrollTo().performClick()
        assertThat(opened).isEqualTo("health")
    }

    @Test
    fun settingsDark() {
        show(dark = true) { SettingsContent(settings.copy(appearance = Appearance.DARK), SettingsActions()) }
        compose.onRoot().captureRoboImage()
    }

    @Test
    fun settingsInTheDemo() {
        var exited = false
        show { SettingsContent(settings.copy(mode = SettingsMode.Demo), SettingsActions(onExitDemo = { exited = true })) }
        compose.onNodeWithText("Demo ledger").assertExists()
        compose.onNodeWithText("Cloud backup").assertDoesNotExist()
        compose.onNodeWithText("Delete account").assertDoesNotExist()
        compose.onNodeWithText("Exit demo").performScrollTo().performClick()
        assertThat(exited).isTrue()
    }

    @Test
    fun turningBackupOffAsksFirst() {
        var off = false
        show { SettingsContent(settings, SettingsActions(onTurnOffBackup = { off = true })) }
        compose.onNodeWithText("Cloud backup").performClick()
        compose.onNodeWithText("Turn off cloud backup?").assertExists()
        assertThat(off).isFalse()
        compose.onNodeWithText("Turn off and delete").performClick()
        assertThat(off).isTrue()
    }

    @Test
    fun askingAboveAnotherAmount() {
        var asked: Long? = 0
        show { SettingsContent(settings, SettingsActions(onAskAbove = { asked = it })) }
        compose.onNodeWithText("Ask to break down").performScrollTo().performClick()
        compose.onNodeWithText("Never").performClick()
        assertThat(asked).isNull()
    }

    @Test
    fun captureHealth() {
        var checked = 0
        show {
            CaptureHealthContent(
                health = CaptureHealth(sms = true, notificationAccess = false, questions = true, battery = false, installedApps = listOf("PhonePe")),
                lastCapturedAt = at(6, 18, 15),
                nowMillis = now,
                onBack = {},
                onOpenSmsSetup = {},
                onOpenNotificationAccess = {},
                onOpenAppNotifications = {},
                onOpenAppInfo = {},
                onOpenBattery = {},
                onCheckAgain = { checked++ },
                restrictedSettings = true,
            )
        }
        compose.onNodeWithText("SMS is working.").assertExists()
        compose.onNodeWithText("2 settings can make capture smoother").assertExists()
        compose.onNodeWithText("For sideloaded apps on Android 13+").assertExists()
        compose.onRoot().captureRoboImage()
        compose.onNodeWithText("Check again").performScrollTo().performClick()
        assertThat(checked).isEqualTo(1)
    }

    private val rules = listOf(
        RuleEntity("r1", RuleField.UPI_ID, RuleMatch.CONTAINS, "swiggy", CategoryIds.FOOD_DELIVERY, 1, "u", 0),
        RuleEntity("r2", RuleField.PAYEE, RuleMatch.EXACT, "Block C Chai", CategoryIds.FOOD_CANTEEN, 2, "u", 0),
        RuleEntity("r3", RuleField.EITHER, RuleMatch.REGEX, "jio|airtel", CategoryIds.RECHARGE, 3, "u", 0),
    )

    @Test
    fun rules() {
        show {
            RulesContent(rules, HomeFixtures.categories, matchCount = null, onBack = {}, onPreview = {}, onSave = { _, _, _, _, _ -> }, onDelete = {}, onMove = { _, _ -> })
        }
        compose.onNodeWithText("Your filing rules.").assertExists()
        compose.onNodeWithText("UPI ID contains “swiggy”").assertExists()
        compose.onRoot().captureRoboImage()
    }

    @Test
    fun addingARuleShowsWhatItWouldCatch() {
        var draft: RuleDraft? = null
        var saved: List<Any?>? = null
        show {
            RulesContent(
                emptyList(), HomeFixtures.categories, matchCount = 4, onBack = {},
                onPreview = { draft = it },
                onSave = { rule, field, match, pattern, category -> saved = listOf(rule, field, match, pattern, category) },
                onDelete = {}, onMove = { _, _ -> },
            )
        }
        compose.onNodeWithText("No rules yet").assertExists()
        compose.onNodeWithText("Add a rule").performClick()
        compose.onNodeWithText("UPI ID").performClick()
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("canteen")
        compose.waitForIdle()
        assertThat(draft).isEqualTo(RuleDraft(RuleField.UPI_ID, RuleMatch.CONTAINS, "canteen"))
        compose.onNodeWithText("Matches 4 payments so far").assertExists()
        compose.onNodeWithText("Food & Canteen").performScrollTo().performClick()
        compose.onNodeWithText("Add rule").performScrollTo().performClick()
        assertThat(saved).isEqualTo(listOf(null, RuleField.UPI_ID, RuleMatch.CONTAINS, "canteen", CategoryIds.FOOD_CANTEEN))
    }

    @Test
    fun editingARule() {
        var saved: RuleEntity? = null
        var savedPattern = ""
        show {
            RulesContent(
                rules, HomeFixtures.categories, matchCount = null, onBack = {}, onPreview = {},
                onSave = { rule, _, _, pattern, _ -> saved = rule; savedPattern = pattern },
                onDelete = {}, onMove = { _, _ -> },
            )
        }
        compose.onNodeWithText("Name is “Block C Chai”").performClick()
        compose.onNodeWithText("Edit rule").assertExists()
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("Block C Chai Stall")
        compose.onNodeWithText("Save rule").performScrollTo().performClick()
        assertThat(saved?.id).isEqualTo("r2")
        assertThat(savedPattern).isEqualTo("Block C Chai Stall")
    }

    private val accounts = listOf(
        MyAccountEntity("a1", AccountMatch.UPI_ID, "hostelledger@oksbi", AccountRole.MINE, "SBI savings", "u", 0),
        MyAccountEntity("a2", AccountMatch.NAME, "RAHUL SHARMA", AccountRole.MINE, null, "u", 0),
        MyAccountEntity("a3", AccountMatch.UPI_ID, "papa.sharma@okhdfc", AccountRole.HOME, "Papa's HDFC", "u", 0),
    )

    @Test
    fun myAccounts() {
        var edited: Pair<MyAccountEntity?, String>? = null
        show {
            MyAccountsContent(accounts, onBack = {}, onSave = { account, _, _, _, label -> edited = account to label }, onRemove = {})
        }
        compose.onNodeWithText("Mine — transfers aren't spending").assertExists()
        compose.onNodeWithText("Home — money in is allowance").assertExists()
        compose.onRoot().captureRoboImage()
        compose.onNodeWithText("Papa's HDFC").performClick()
        compose.onNodeWithText("Edit account").assertExists()
        compose.onAllNodes(hasSetTextAction())[1].performTextReplacement("Papa's HDFC account")
        compose.onNodeWithText("Save account").performClick()
        assertThat(edited?.first?.id).isEqualTo("a3")
        assertThat(edited?.second).isEqualTo("Papa's HDFC account")
    }

    @Test
    fun aUpiIdNeedsAnAt() {
        show { MyAccountsContent(emptyList(), onBack = {}, onSave = { _, _, _, _, _ -> }, onRemove = {}) }
        compose.onNodeWithText("Add an account").performClick()
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("rahul")
        compose.onNodeWithText("A UPI ID has an @ in it, like you@oksbi").assertExists()
    }

    @Test
    fun privacyAndData() {
        var password: String? = null
        show {
            DataContent(
                DataState(lockOn = true), onBack = {}, onLock = {}, onExportCsv = {}, onExportJson = {},
                onSaveBackup = { password = it }, onRestore = {},
            )
        }
        compose.onNodeWithText("What leaves this phone").assertExists()
        compose.onRoot().captureRoboImage()
        compose.onNodeWithText("Save a backup file").performScrollTo().performClick()
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("chai-at-the-tapri")
        compose.onAllNodes(hasSetTextAction())[1].performTextInput("chai-at-the-tapri")
        compose.onNodeWithText("Choose where to save").performClick()
        assertThat(password).isEqualTo("chai-at-the-tapri")
    }

    @Test
    fun privacyAndDataInTheDemo() {
        show {
            DataContent(DataState(lockAvailable = false, isDemo = true), onBack = {}, onLock = {}, onExportCsv = {}, onExportJson = {}, onSaveBackup = {}, onRestore = {})
        }
        compose.onNodeWithText("Log in to restore; the demo's data is made up.").assertExists()
        compose.onAllNodesWithText("Set a screen lock on your phone first (Settings → Security).").assertCountEquals(1)
    }
}
