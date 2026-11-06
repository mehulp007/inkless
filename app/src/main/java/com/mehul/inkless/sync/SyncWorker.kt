package com.mehul.inkless.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestoreException
import com.mehul.inkless.capture.CaptureRunner
import com.mehul.inkless.session.SessionPrefs
import com.mehul.inkless.splits.SharedSplits
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
        fun sharedSplits(): SharedSplits
        fun sessionPrefs(): SessionPrefs
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
        val (uid, db) = deps.runner().currentAccount() ?: return Result.success()
        val outcome = deps.syncer().sync(uid, db)
        // Splits shared with friends go out whether or not backup is on.
        if (outcome != SyncOutcome.OFFLINE) {
            try {
                deps.sharedSplits().sync(uid, deps.sessionPrefs().username(uid), db)
            } catch (e: FirebaseFirestoreException) {
                Log.w("SyncWorker", "Shared splits: ${e.code}")
                return Result.retry()
            }
        }
        return when (outcome) {
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
