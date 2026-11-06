package com.mehul.inkless.sync

import android.util.Log
import androidx.room.withTransaction
import com.google.firebase.firestore.FirebaseFirestoreException
import com.mehul.inkless.data.db.InklessDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

enum class SyncOutcome { DONE, BACKUP_OFF, OFFLINE, FAILED }

/**
 * Two-way sync between the phone's database and the account's cloud copy. The phone stays the
 * source of truth: download whatever changed in the cloud since last time, then upload every row
 * with unsent changes. When both sides changed the same row, the later edit (by `updatedAt`) wins.
 * Downloading first means a phone never uploads an edit that's older than the cloud's; the
 * Firestore rules refuse one anyway. Deletions are rows marked `deleted`, so they travel like any
 * other change.
 */
@Singleton
class Syncer @Inject constructor(
    private val cloud: CloudStore,
    private val prefs: SyncPrefs,
    private val clock: Clock,
) {
    private val mutex = Mutex()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _lastProblem = MutableStateFlow<SyncOutcome?>(null)

    /** OFFLINE or FAILED when the last attempt didn't finish, null when it did. */
    val lastProblem: StateFlow<SyncOutcome?> = _lastProblem.asStateFlow()

    suspend fun sync(uid: String, db: InklessDatabase): SyncOutcome = mutex.withLock {
        _running.value = true
        try {
            val outcome = try {
                run(uid, db)
            } catch (e: OfflineException) {
                SyncOutcome.OFFLINE
            } catch (e: FirebaseFirestoreException) {
                Log.w(TAG, "Sync failed: ${e.code}")
                if (e.code == FirebaseFirestoreException.Code.UNAVAILABLE) SyncOutcome.OFFLINE else SyncOutcome.FAILED
            }
            _lastProblem.value = outcome.takeIf { it == SyncOutcome.OFFLINE || it == SyncOutcome.FAILED }
            outcome
        } finally {
            _running.value = false
        }
    }

    private suspend fun run(uid: String, db: InklessDatabase): SyncOutcome {
        if (!cloud.backupEnabled(uid)) {
            prefs.setBackupOn(uid, false)
            // Switched off here earlier, but deleting the cloud copy didn't finish then.
            if (prefs.erasePending(uid)) {
                cloud.eraseAll(uid)
                prefs.setErasePending(uid, false)
            }
            return SyncOutcome.BACKUP_OFF
        }
        // Switched back on (here or on another phone): the cloud may have nothing, so send everything.
        if (prefs.backupOn(uid) == false) startOver(uid, db)
        prefs.setBackupOn(uid, true)
        prefs.setErasePending(uid, false)

        for (table in SyncTables.all) pull(uid, db, table)
        for (table in SyncTables.all) push(uid, db, table)
        prefs.setLastSyncedAt(uid, clock.millis())
        return SyncOutcome.DONE
    }

    /**
     * Turns backup off for the whole account and deletes the cloud copy; the phone keeps everything.
     * Returns false if the deleting was cut short, in which case the next sync finishes it. Throws
     * when the switch itself can't be reached, and then nothing has changed.
     */
    suspend fun switchOff(uid: String): Boolean = mutex.withLock {
        cloud.setBackupEnabled(uid, false)
        prefs.setBackupOn(uid, false)
        prefs.setErasePending(uid, true)
        prefs.resetCursors(uid)
        try {
            cloud.eraseAll(uid)
            prefs.setErasePending(uid, false)
            true
        } catch (e: OfflineException) {
            false
        } catch (e: FirebaseFirestoreException) {
            false
        }
    }

    /** Turns backup back on and uploads everything, since the cloud copy was deleted. Throws like [switchOff]. */
    suspend fun switchOn(uid: String, db: InklessDatabase): SyncOutcome {
        mutex.withLock {
            cloud.setBackupEnabled(uid, true)
            startOver(uid, db)
            prefs.setBackupOn(uid, true)
        }
        return sync(uid, db)
    }

    private suspend fun startOver(uid: String, db: InklessDatabase) {
        db.withTransaction { SyncTables.all.forEach { it.markAllDirty(db.syncDao()) } }
        prefs.resetCursors(uid)
    }

    private suspend fun <E> push(uid: String, db: InklessDatabase, table: SyncTable<E>) {
        val dao = db.syncDao()
        repeat(MAX_ROUNDS) {
            val rows = table.dirty(dao, PAGE_SIZE)
            if (rows.isEmpty()) return
            cloud.write(uid, table.collection, rows.map { table.id(it) to table.toDoc(it) })
            db.withTransaction { rows.forEach { table.markSynced(dao, table.id(it), table.updatedAt(it)) } }
        }
    }

    private suspend fun <E> pull(uid: String, db: InklessDatabase, table: SyncTable<E>) {
        // A little overlap costs a few re-reads and guards against a write landing as we read.
        val since = prefs.cursor(uid, table.collection)?.minus(PULL_OVERLAP_MS)
        val docs = cloud.changedSince(uid, table.collection, since)
        if (docs.isEmpty()) return

        val dao = db.syncDao()
        docs.mapNotNull { table.fromDoc(it.id, it.data, uid) }.chunked(PAGE_SIZE).forEach { remote ->
            db.withTransaction {
                val local = table.local(dao, remote.map(table.id))
                table.save(dao, table.winners(remote, local))
            }
        }
        prefs.setCursor(uid, table.collection, docs.maxOf { it.syncedAt })
    }

    private companion object {
        const val TAG = "Syncer"
        const val PAGE_SIZE = 400
        const val MAX_ROUNDS = 100
        const val PULL_OVERLAP_MS = 60_000L
    }
}
