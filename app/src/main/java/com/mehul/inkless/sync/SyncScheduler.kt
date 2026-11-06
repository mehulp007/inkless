package com.mehul.inkless.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.mehul.inkless.data.db.DatabaseProvider
import com.mehul.inkless.di.ApplicationScope
import com.mehul.inkless.session.Session
import com.mehul.inkless.session.SessionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides when to sync: right after logging in (that's what restores a new phone), a few seconds
 * after any change, and every few hours as a safety net. The work itself runs in [SyncWorker].
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Singleton
class SyncScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val sessions: SessionManager,
    private val databases: DatabaseProvider,
    private val prefs: SyncPrefs,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val work get() = WorkManager.getInstance(context)

    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun start() {
        scope.launch {
            sessions.session
                .map { (it as? Session.SignedIn)?.user?.uid }
                .distinctUntilChanged()
                .collectLatest { uid ->
                    if (uid == null) {
                        work.cancelUniqueWork(PERIODIC_WORK)
                        return@collectLatest
                    }
                    schedulePeriodic()
                    syncSoon()
                    // Waits for edits, captures and imports to settle, then sends them in one go.
                    databases.current.filterNotNull()
                        .flatMapLatest { it.syncDao().observePending() }
                        .debounce(QUIET_BEFORE_SYNC_MS)
                        // With backup off nothing goes up; the periodic run notices if it's switched back on.
                        .filter { it > 0 && prefs.backupOn(uid) != false }
                        .collect { syncSoon() }
                }
        }
    }

    fun syncSoon() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(online)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofSeconds(30))
            .build()
        // A sync already waiting or running sends everything that's pending by the time it reads.
        work.enqueueUniqueWork(SYNC_WORK, ExistingWorkPolicy.KEEP, request)
    }

    private fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(Duration.ofHours(6))
            .setConstraints(online)
            .build()
        work.enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private companion object {
        const val SYNC_WORK = "sync"
        const val PERIODIC_WORK = "sync-periodic"
        const val QUIET_BEFORE_SYNC_MS = 5_000L
    }
}
