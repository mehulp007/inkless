package com.mehul.inkless.ui.settings

import com.mehul.inkless.sync.BackupStatus
import com.mehul.inkless.sync.SyncOutcome
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The one line under "Back up to the cloud" in Settings. */
object BackupText {

    private val dayFormat = DateTimeFormatter.ofPattern("d MMM")

    fun describe(status: BackupStatus, nowMillis: Long, zone: ZoneId): String {
        if (!status.on) return "Off. Everything stays on this phone only"
        if (status.running) return if (status.lastSyncedAt == null) "Restoring your backup…" else "Backing up…"
        val waiting = changes(status.pending)
        return when {
            status.problem == SyncOutcome.OFFLINE -> "Waiting for internet" + waiting?.let { " · $it not backed up" }.orEmpty()
            status.problem == SyncOutcome.FAILED -> "Couldn't back up. Tap Back up now to try again"
            status.lastSyncedAt == null -> "Not backed up yet"
            else -> "Backed up ${ago(status.lastSyncedAt, nowMillis, zone)}" + waiting?.let { " · $it waiting" }.orEmpty()
        }
    }

    fun changes(count: Int): String? = when {
        count <= 0 -> null
        count == 1 -> "1 change"
        else -> "$count changes"
    }

    private fun ago(millis: Long, nowMillis: Long, zone: ZoneId): String {
        val minutes = (nowMillis - millis) / 60_000
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "$minutes min ago"
            minutes < 24 * 60 -> "${minutes / 60} h ago"
            else -> "on " + dayFormat.format(Instant.ofEpochMilli(millis).atZone(zone))
        }
    }
}
