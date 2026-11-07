package com.mehul.inkless.ui.budget

import com.google.common.truth.Truth.assertThat
import com.mehul.inkless.budget.BudgetMath
import java.time.LocalDate
import org.junit.Test

class AllowanceTextTest {

    @Test
    fun `ordinals`() {
        assertThat(listOf(1, 2, 3, 4, 11, 12, 13, 21, 22, 23, 31).map(::ordinal))
            .containsExactly("1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "21st", "22nd", "23rd", "31st")
            .inOrder()
    }

    @Test
    fun `budget month from the first`() {
        val cycle = BudgetMath.cycle(LocalDate.of(2026, 11, 7), 1)
        assertThat(cycleLabel(cycle)).isEqualTo("1 Nov – 30 Nov 2026")
    }

    @Test
    fun `budget month that started last month`() {
        val cycle = BudgetMath.cycle(LocalDate.of(2026, 11, 7), 25)
        assertThat(cycleLabel(cycle)).isEqualTo("25 Oct – 24 Nov 2026")
    }
}
