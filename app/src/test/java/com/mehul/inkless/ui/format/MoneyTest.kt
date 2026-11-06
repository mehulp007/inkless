package com.mehul.inkless.ui.format

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MoneyTest {

    @Test
    fun `small amounts`() {
        assertThat(Money.format(0)).isEqualTo("₹0")
        assertThat(Money.format(4500)).isEqualTo("₹45")
        assertThat(Money.format(4550)).isEqualTo("₹45.50")
        assertThat(Money.format(5)).isEqualTo("₹0.05")
    }

    @Test
    fun `indian grouping`() {
        assertThat(Money.format(99_900)).isEqualTo("₹999")
        assertThat(Money.format(1_234_00)).isEqualTo("₹1,234")
        assertThat(Money.format(12_345_650)).isEqualTo("₹1,23,456.50")
        assertThat(Money.format(1_00_00_000_00)).isEqualTo("₹1,00,00,000")
    }

    @Test
    fun `negative amounts`() {
        assertThat(Money.format(-4500)).isEqualTo("-₹45")
        assertThat(Money.format(-1_234_56)).isEqualTo("-₹1,234.56")
    }

    @Test
    fun `can force paise`() {
        assertThat(Money.format(4500, alwaysShowPaise = true)).isEqualTo("₹45.00")
    }

    @Test
    fun `short amounts for chart labels`() {
        assertThat(Money.short(95_000)).isEqualTo("₹950")
        assertThat(Money.short(120_000)).isEqualTo("₹1.2k")
        assertThat(Money.short(1_000_000)).isEqualTo("₹10k")
        assertThat(Money.short(15_000_000)).isEqualTo("₹1.5L")
        assertThat(Money.short(-500)).isEqualTo("₹0")
    }
}
