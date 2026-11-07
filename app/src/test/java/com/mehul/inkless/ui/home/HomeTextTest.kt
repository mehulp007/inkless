package com.mehul.inkless.ui.home

import com.google.common.truth.Truth.assertThat
import com.mehul.inkless.data.db.entity.CaptureSource
import com.mehul.inkless.ui.category.monthRange
import com.mehul.inkless.ui.category.sourcesLabel
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Test

class HomeTextTest {

    private val rows = HomeFixtures.state.tabs.first().days.flatMap { it.rows }.associateBy { it.id }

    @Test
    fun `each kind of payment says what it is`() {
        assertThat(paymentMeta(rows.getValue("s"), "13:10")).isEqualTo("Food & Canteen · 13:10")
        assertThat(paymentMeta(rows.getValue("q"), "17:40")).isEqualTo("Other · 17:40 · To sort")
        assertThat(paymentMeta(rows.getValue("sr"), "17:00")).isEqualTo("Food Delivery · Category offset")
        assertThat(paymentMeta(rows.getValue("sbi"), "20:00")).isEqualTo("Own-account transfer · Excluded")
        assertThat(paymentMeta(rows.getValue("a"), "1 Nov")).isEqualTo("1 Nov · Income · Not spending")
        assertThat(paymentMeta(rows.getValue("s").copy(friendsSharePaise = 6_000), "13:10"))
            .isEqualTo("Food & Canteen · 13:10 · Your part ₹30")
    }

    @Test
    fun `earlier this month in one line`() {
        val earlier = HomeFixtures.state.tabs.first().earlier!!
        assertThat(dateRange(earlier)).isEqualTo("2–4 Nov")
        assertThat(categorySummary(earlier)).isEqualTo("Food, college & recharge")
        assertThat(earlier.paymentCount).isEqualTo(3)
        assertThat(earlier.income.single().title).isEqualTo("Monthly allowance")
        assertThat(dateRange(earlier.copy(from = LocalDate.of(2026, 10, 30)))).isEqualTo("30 Oct – 4 Nov")
        assertThat(categorySummary(earlier.copy(moreCategories = true)))
            .isEqualTo("Food, college, recharge & more")
    }

    @Test
    fun `day headings`() {
        val today = HomeFixtures.today
        assertThat(dayLabel(today, today)).isEqualTo("Today · Fri, 6 Nov")
        assertThat(dayLabel(today.minusDays(1), today)).isEqualTo("Yesterday · 5 Nov")
        assertThat(dayLabel(today.minusDays(2), today)).isEqualTo("Wed, 4 Nov")
    }

    @Test
    fun `yesterday is net of the Swiggy refund`() {
        val yesterday = HomeFixtures.state.tabs.first().recent[1]
        assertThat(yesterday.net).isTrue()
        // 420 + 240 − 120 + 200 + 80; the transfer to SBI doesn't count.
        assertThat(yesterday.spentPaise).isEqualTo(82_000)
    }

    @Test
    fun `what's safe after the cash is saved`() {
        assertThat(safeAfter(HomeFixtures.snapshot, 4_000)).isEqualTo("Safe today after saving: ₹44")
        assertThat(safeAfter(HomeFixtures.snapshot, 10_000)).isEqualTo("Over today's share after saving: ₹16")
    }

    @Test
    fun `where a payment came from`() {
        assertThat(sourcesLabel(setOf(CaptureSource.NOTIFICATION, CaptureSource.SMS))).isEqualTo("Bank SMS + notification")
        assertThat(sourcesLabel(setOf(CaptureSource.SMS))).isEqualTo("Bank SMS")
        assertThat(sourcesLabel(setOf(CaptureSource.NOTIFICATION))).isEqualTo("Notification")
        assertThat(sourcesLabel(setOf(CaptureSource.MANUAL))).isEqualTo("Cash")
    }

    @Test
    fun `the category page's month`() {
        assertThat(monthRange(YearMonth.of(2026, 11))).isEqualTo("1–30 November 2026")
        assertThat(monthRange(YearMonth.of(2027, 2))).isEqualTo("1–28 February 2027")
    }
}
