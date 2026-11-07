package com.mehul.inkless.ui.settings

import com.google.common.truth.Truth.assertThat
import com.mehul.inkless.categorize.RuleField
import com.mehul.inkless.categorize.RuleMatch
import com.mehul.inkless.data.db.entity.AccountMatch
import com.mehul.inkless.data.db.entity.AccountRole
import com.mehul.inkless.data.db.entity.BudgetEntity
import com.mehul.inkless.data.db.entity.BudgetPeriod
import com.mehul.inkless.data.db.entity.MyAccountEntity
import com.mehul.inkless.ui.accounts.AccountText
import com.mehul.inkless.ui.health.CaptureHealth
import com.mehul.inkless.ui.health.HealthText
import com.mehul.inkless.ui.rules.RuleDraft
import com.mehul.inkless.ui.rules.RuleText
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Test

class SettingsTextTest {

    private fun budget(paise: Long, day: Int?) = BudgetEntity(BudgetEntity.MONTHLY_ID, BudgetPeriod.MONTH, paise, day, null, "u", 0)

    private fun account(value: String, label: String? = null, role: AccountRole = AccountRole.MINE, match: AccountMatch = AccountMatch.UPI_ID) =
        MyAccountEntity(value, match, value, role, label, "u", 0)

    @Test
    fun `allowance line says how much and when it arrives`() {
        assertThat(SettingsText.allowance(budget(12_00_000, 1))).isEqualTo("₹12,000 · arrives on the 1st")
        assertThat(SettingsText.allowance(budget(8_00_000, 22))).isEqualTo("₹8,000 · arrives on the 22nd")
        assertThat(SettingsText.allowance(budget(8_00_000, null))).isEqualTo("₹8,000 a month")
        assertThat(SettingsText.allowance(null)).isEqualTo("Not set yet")
        assertThat(SettingsText.allowance(budget(0, 1))).isEqualTo("Not set yet")
    }

    @Test
    fun `categories line counts rules`() {
        assertThat(SettingsText.categories(13, 0)).isEqualTo("13 categories · no rules yet")
        assertThat(SettingsText.categories(13, 1)).isEqualTo("13 categories · 1 rule")
        assertThat(SettingsText.categories(14, 3)).isEqualTo("14 categories · 3 rules")
    }

    @Test
    fun `accounts line shows labels first and how many more`() {
        assertThat(SettingsText.accounts(emptyList())).isEqualTo("None added yet")
        assertThat(SettingsText.accounts(listOf(account("me@oksbi", "SBI savings"), account("me@okhdfc"))))
            .isEqualTo("SBI savings · me@okhdfc")
        assertThat(SettingsText.accounts(listOf(account("a@x"), account("b@x"), account("c@x"), account("d@x"))))
            .isEqualTo("a@x · b@x +2")
    }

    @Test
    fun `ask above line`() {
        assertThat(SettingsText.askAbove(10_000)).isEqualTo("After payments over ₹100")
        assertThat(SettingsText.askAbove(null)).isEqualTo("Never. Break one down from its page")
    }

    @Test
    fun `capture health counts what's left to check`() {
        val allOn = CaptureHealth(sms = true, notificationAccess = true, questions = true, battery = true)
        assertThat(allOn.toCheck).isEqualTo(0)
        assertThat(allOn.settingsLine).isEqualTo("SMS active · all good")
        assertThat(allOn.summary).isEqualTo("Everything capture needs is on")

        val figma = allOn.copy(notificationAccess = false, battery = false)
        assertThat(figma.headline).isEqualTo("SMS is working.")
        assertThat(figma.summary).isEqualTo("2 settings can make capture smoother")
        assertThat(figma.settingsLine).isEqualTo("SMS active · 2 things to check")

        val noSms = CaptureHealth(sms = false, notificationAccess = true, questions = false, battery = true)
        assertThat(noSms.headline).isEqualTo("SMS is off.")
        assertThat(noSms.settingsLine).isEqualTo("SMS off · 1 thing to check")
    }

    @Test
    fun `last captured reads as today, yesterday or a date`() {
        val zone = ZoneOffset.ofHoursMinutes(5, 30)
        fun at(day: Int, hour: Int, minute: Int) = LocalDateTime.of(2026, 11, day, hour, minute).atZone(zone).toInstant().toEpochMilli()
        val now = at(6, 21, 0)
        assertThat(HealthText.lastCaptured(at(6, 18, 15), now, zone)).isEqualTo("Last payment captured today at 18:15.")
        assertThat(HealthText.lastCaptured(at(5, 9, 5), now, zone)).isEqualTo("Last payment captured yesterday at 09:05.")
        assertThat(HealthText.lastCaptured(at(1, 13, 40), now, zone)).isEqualTo("Last payment captured on 1 Nov at 13:40.")
        assertThat(HealthText.lastCaptured(null, now, zone)).isEqualTo("No payment captured yet.")
    }

    @Test
    fun `rules read as sentences`() {
        assertThat(RuleText.describe(RuleField.UPI_ID, RuleMatch.CONTAINS, "swiggy")).isEqualTo("UPI ID contains “swiggy”")
        assertThat(RuleText.describe(RuleField.PAYEE, RuleMatch.EXACT, "Sharma Canteen")).isEqualTo("Name is “Sharma Canteen”")
        assertThat(RuleText.describe(RuleField.EITHER, RuleMatch.REGEX, "jio|airtel")).isEqualTo("UPI ID or name matches “jio|airtel”")
        assertThat(RuleText.matches(0)).isEqualTo("Matches no payments yet")
        assertThat(RuleText.matches(1)).isEqualTo("Matches 1 payment so far")
        assertThat(RuleText.matches(4)).isEqualTo("Matches 4 payments so far")
        assertThat(RuleText.saved(added = false, filed = 2)).isEqualTo("Rule saved and 2 waiting payments filed.")
    }

    @Test
    fun `a draft rule needs text and a pattern that compiles`() {
        assertThat(RuleDraft(RuleField.EITHER, RuleMatch.CONTAINS, "  ").valid).isFalse()
        assertThat(RuleDraft(RuleField.EITHER, RuleMatch.CONTAINS, "[").valid).isTrue()
        assertThat(RuleDraft(RuleField.EITHER, RuleMatch.REGEX, "[").valid).isFalse()
        assertThat(RuleDraft(RuleField.EITHER, RuleMatch.REGEX, "jio|airtel").valid).isTrue()
    }

    @Test
    fun `account sheet wants an @ in a UPI ID`() {
        assertThat(AccountText.problem(AccountMatch.UPI_ID, "")).isNull()
        assertThat(AccountText.problem(AccountMatch.UPI_ID, "rahul")).isNotNull()
        assertThat(AccountText.problem(AccountMatch.UPI_ID, "rahul@oksbi")).isNull()
        assertThat(AccountText.problem(AccountMatch.NAME, "RAHUL SHARMA")).isNull()
        assertThat(AccountText.meta(account("rahul@oksbi", "SBI savings"))).isEqualTo("UPI ID · rahul@oksbi")
        assertThat(AccountText.meta(account("RAHUL SHARMA", match = AccountMatch.NAME))).isEqualTo("Name")
    }
}
