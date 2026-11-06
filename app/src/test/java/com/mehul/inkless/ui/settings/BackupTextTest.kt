package com.mehul.inkless.ui.settings

import com.google.common.truth.Truth.assertThat
import com.mehul.inkless.sync.BackupStatus
import com.mehul.inkless.sync.SyncOutcome
import org.junit.Test
import java.time.ZoneOffset

class BackupTextTest {

    private val now = 1_800_000_000_000L
    private val minute = 60_000L

    private fun text(status: BackupStatus) = BackupText.describe(status, now, ZoneOffset.UTC)

    @Test
    fun `says when it last backed up and what's still waiting`() {
        assertThat(text(BackupStatus(lastSyncedAt = now - 20_000))).isEqualTo("Backed up just now")
        assertThat(text(BackupStatus(lastSyncedAt = now - 5 * minute, pending = 1))).isEqualTo("Backed up 5 min ago · 1 change waiting")
        assertThat(text(BackupStatus(lastSyncedAt = now - 180 * minute, pending = 3))).isEqualTo("Backed up 3 h ago · 3 changes waiting")
        assertThat(text(BackupStatus(lastSyncedAt = now - 3 * 24 * 60 * minute))).isEqualTo("Backed up on 12 Jan")
    }

    @Test
    fun `restoring, offline, failed and off each get their own line`() {
        assertThat(text(BackupStatus(running = true))).isEqualTo("Restoring your backup…")
        assertThat(text(BackupStatus(running = true, lastSyncedAt = now))).isEqualTo("Backing up…")
        assertThat(text(BackupStatus(lastSyncedAt = now, pending = 2, problem = SyncOutcome.OFFLINE)))
            .isEqualTo("Waiting for internet · 2 changes not backed up")
        assertThat(text(BackupStatus(problem = SyncOutcome.FAILED))).isEqualTo("Couldn't back up. Tap Back up now to try again")
        assertThat(text(BackupStatus(on = false, pending = 7))).isEqualTo("Off. Everything stays on this phone only")
        assertThat(text(BackupStatus())).isEqualTo("Not backed up yet")
    }

    @Test
    fun `restoring is only the first download on a phone`() {
        assertThat(BackupStatus(running = true).restoring).isTrue()
        assertThat(BackupStatus(running = true, lastSyncedAt = now).restoring).isFalse()
        assertThat(BackupStatus(on = false, running = true).restoring).isFalse()
    }
}
