package com.mehul.inkless.parsing

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AmountsTest {

    @Test
    fun `plain and grouped amounts`() {
        assertThat(Amounts.toPaise("45")).isEqualTo(4500)
        assertThat(Amounts.toPaise("1,234.50")).isEqualTo(123450)
        assertThat(Amounts.toPaise("2,10,000.00")).isEqualTo(21000000)
    }

    @Test
    fun `single decimal digit means tens of paise`() {
        assertThat(Amounts.toPaise("12.5")).isEqualTo(1250)
        assertThat(Amounts.toPaise("12.05")).isEqualTo(1205)
    }

    @Test
    fun `rejects junk`() {
        assertThat(Amounts.toPaise("")).isNull()
        assertThat(Amounts.toPaise("12.345")).isNull()
        assertThat(Amounts.toPaise("1.2.3")).isNull()
        assertThat(Amounts.toPaise("abc")).isNull()
        assertThat(Amounts.toPaise(".50")).isNull()
    }

    @Test
    fun `finds the amount inside a message`() {
        assertThat(Amounts.findPaise("Rs.1,250.00 debited from A/c XX4821")).isEqualTo(125000)
        assertThat(Amounts.findPaise("Sent INR 45 to q829184562@ybl")).isEqualTo(4500)
        assertThat(Amounts.findPaise("You paid ₹30 to Tapri")).isEqualTo(3000)
        assertThat(Amounts.findPaise("rs 99.9 spent")).isEqualTo(9990)
    }

    @Test
    fun `no currency marker means no amount`() {
        assertThat(Amounts.findPaise("Your OTP is 482913")).isNull()
    }
}
