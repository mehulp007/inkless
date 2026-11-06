package com.mehul.inkless.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mehul.inkless.capture.CaptureRunner
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

/** Runs a sync for whoever is signed in. WorkManager only starts it with a network connection. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun runner(): CaptureRunner
        fun syncer(): Syncer
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
        val (uid, db) = deps.runner().currentAccount() ?: return Result.success()
        return when (deps.syncer().sync(uid, db)) {
            // Something changed while this run was busy downloading: go again shortly.
            SyncOutcome.DONE -> if (db.syncDao().observePending().first() > 0 && runAttemptCount < MAX_ATTEMPTS) {
                Result.retry()
            } else {
                Result.success()
            }
            SyncOutcome.BACKUP_OFF -> Result.success()
            SyncOutcome.OFFLINE -> Result.retry()
            SyncOutcome.FAILED -> if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
    }
}
