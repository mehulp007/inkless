package com.mehul.inkless.sync

import com.mehul.inkless.data.db.DatabaseProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

data class BackupStatus(
    val on: Boolean = true,
    val running: Boolean = false,
    val lastSyncedAt: Long? = null,
    /** Rows changed on this phone that the cloud hasn't got yet. */
    val pending: Int = 0,
    val problem: SyncOutcome? = null,
) {
    /** A new phone pulling down the backup for the first time. */
    val restoring: Boolean get() = on && running && lastSyncedAt == null
}

/** What screens see of the cloud backup, and the buttons they offer. */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class CloudBackup @Inject constructor(
    private val syncer: Syncer,
    private val prefs: SyncPrefs,
    private val databases: DatabaseProvider,
) {
    fun status(uid: String): Flow<BackupStatus> {
        val pending = databases.current.flatMapLatest { it?.syncDao()?.observePending() ?: flowOf(0) }
        return combine(prefs.changes, syncer.running, syncer.lastProblem, pending) { _, running, problem, count ->
            BackupStatus(
                on = prefs.backupOn(uid) ?: true,
                running = running,
                lastSyncedAt = prefs.lastSyncedAt(uid),
                pending = count,
                problem = problem,
            )
        }
    }

    suspend fun syncNow(uid: String): SyncOutcome {
        val db = databases.current.value ?: return SyncOutcome.FAILED
        return syncer.sync(uid, db)
    }

    /** False when part of the cloud copy is still to be deleted (it goes on the next sync). */
    suspend fun turnOff(uid: String): Boolean = syncer.switchOff(uid)

    suspend fun turnOn(uid: String): SyncOutcome {
        val db = databases.current.value ?: return SyncOutcome.FAILED
        return syncer.switchOn(uid, db)
    }
}
