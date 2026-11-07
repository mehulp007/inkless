package com.mehul.inkless.ui.onboarding

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SetupStepTest {

    @Test
    fun `with SMS on, every step in turn`() {
        val visited = generateSequence(SetupStep.Capture) { it.next(smsOn = true).takeIf { next -> next != it } }.toList()
        assertThat(visited).containsExactly(SetupStep.Capture, SetupStep.Allowance, SetupStep.Import, SetupStep.Finish).inOrder()
    }

    @Test
    fun `without SMS there is nothing to import`() {
        assertThat(SetupStep.Allowance.next(smsOn = false)).isEqualTo(SetupStep.Finish)
        assertThat(SetupStep.Finish.previous(smsOn = false)).isEqualTo(SetupStep.Allowance)
    }

    @Test
    fun `back walks the steps and leaves from the first`() {
        assertThat(SetupStep.Finish.previous(smsOn = true)).isEqualTo(SetupStep.Import)
        assertThat(SetupStep.Import.previous(smsOn = true)).isEqualTo(SetupStep.Allowance)
        assertThat(SetupStep.Allowance.previous(smsOn = true)).isEqualTo(SetupStep.Capture)
        assertThat(SetupStep.Capture.previous(smsOn = true)).isNull()
    }
}
