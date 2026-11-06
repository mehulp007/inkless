package com.mehul.inkless.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** Per-account sync bookkeeping on this phone. */
@Singleton
class SyncPrefs(context: Context, fileName: String) {

    @Inject constructor(@ApplicationContext context: Context) : this(context, "sync")

    private val prefs = context.getSharedPreferences(fileName, Context.MODE_PRIVATE)

    private val _changes = MutableStateFlow(0)

    /** Ticks whenever something here changes, for screens showing the backup status. */
    val changes: StateFlow<Int> = _changes.asStateFlow()

    /** What this phone last heard from the account's backup switch. Null before the first sync. */
    fun backupOn(uid: String): Boolean? =
        if (prefs.contains(key(uid, "backup"))) prefs.getBoolean(key(uid, "backup"), true) else null

    fun setBackupOn(uid: String, on: Boolean) = write { putBoolean(key(uid, "backup"), on) }

    fun lastSyncedAt(uid: String): Long? = prefs.getLong(key(uid, "last_synced"), 0L).takeIf { it > 0 }

    fun setLastSyncedAt(uid: String, millis: Long) = write { putLong(key(uid, "last_synced"), millis) }

    /** Server time of the newest document already pulled from [collection]. */
    fun cursor(uid: String, collection: String): Long? =
        prefs.getLong(key(uid, "cursor_$collection"), 0L).takeIf { it > 0 }

    fun setCursor(uid: String, collection: String, millis: Long) = write { putLong(key(uid, "cursor_$collection"), millis) }

    fun resetCursors(uid: String) = write {
        SyncTables.collections.forEach { remove(key(uid, "cursor_$it")) }
    }

    /** Backup was switched off but the cloud copy isn't fully deleted yet. */
    fun erasePending(uid: String): Boolean = prefs.getBoolean(key(uid, "erase_pending"), false)

    fun setErasePending(uid: String, pending: Boolean) = write { putBoolean(key(uid, "erase_pending"), pending) }

    /** Forgets the account entirely, when it's deleted. */
    fun forget(uid: String) = write {
        prefs.all.keys.filter { it.startsWith("${uid}_") }.forEach(::remove)
    }

    private fun write(block: SharedPreferences.Editor.() -> Unit) {
        prefs.edit(action = block)
        _changes.update { it + 1 }
    }

    private fun key(uid: String, name: String) = "${uid}_$name"
}
