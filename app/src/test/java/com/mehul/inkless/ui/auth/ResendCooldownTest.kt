package com.mehul.inkless.ui.auth

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ResendCooldownTest {

    @Test
    fun `counts down a minute to zero`() = runTest {
        val cooldown = ResendCooldown(backgroundScope)
        assertThat(cooldown.secondsLeft.value).isEqualTo(0)

        cooldown.start()
        runCurrent()
        assertThat(cooldown.secondsLeft.value).isEqualTo(60)

        advanceTimeBy(1_000)
        runCurrent()
        assertThat(cooldown.secondsLeft.value).isEqualTo(59)

        advanceTimeBy(59_000)
        runCurrent()
        assertThat(cooldown.secondsLeft.value).isEqualTo(0)
    }

    @Test
    fun `sending again starts the minute over`() = runTest {
        val cooldown = ResendCooldown(backgroundScope)
        cooldown.start()
        advanceTimeBy(30_000)
        runCurrent()
        assertThat(cooldown.secondsLeft.value).isEqualTo(30)

        cooldown.start()
        runCurrent()
        assertThat(cooldown.secondsLeft.value).isEqualTo(60)
        advanceTimeBy(45_000)
        runCurrent()
        assertThat(cooldown.secondsLeft.value).isEqualTo(15)
    }

    @Test
    fun `formats as minutes and seconds`() {
        assertThat(ResendCooldown.format(59)).isEqualTo("0:59")
        assertThat(ResendCooldown.format(60)).isEqualTo("1:00")
        assertThat(ResendCooldown.format(5)).isEqualTo("0:05")
    }
}
