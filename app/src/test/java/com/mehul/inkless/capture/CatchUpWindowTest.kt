package com.mehul.inkless.capture

import com.google.common.truth.Truth.assertThat
import com.mehul.inkless.capture.sms.InboxImportWorker.Companion.catchUpSince
import org.junit.Test

class CatchUpWindowTest {

    private val hour = 60 * 60 * 1000L
    private val enabledAt = 100 * hour

    @Test
    fun `nothing captured yet starts at the moment capture was switched on`() {
        // Skipping the history import must not pull in older messages later.
        assertThat(catchUpSince(latestCaptured = null, enabledAt = enabledAt)).isEqualTo(enabledAt)
    }

    @Test
    fun `picks up from an hour before the newest captured sms`() {
        assertThat(catchUpSince(latestCaptured = 150 * hour, enabledAt = enabledAt)).isEqualTo(149 * hour)
    }

    @Test
    fun `never reaches back before capture was switched on`() {
        // A history import can store SMS older than the switch-on time.
        assertThat(catchUpSince(latestCaptured = enabledAt + hour / 2, enabledAt = enabledAt)).isEqualTo(enabledAt)
    }
}
