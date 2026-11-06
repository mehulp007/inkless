package com.mehul.inkless.capture.sms

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.mehul.inkless.capture.CaptureOutcome
import com.mehul.inkless.capture.CaptureProcessor
import com.mehul.inkless.capture.CaptureRunner
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Duration

/**
 * Imports past SMS in the background through WorkManager, which keeps the job going (and retries
 * it) even if the user leaves the screen or the app is closed.
 *
 * Two modes: HISTORY reads back a number of months (first setup); CATCH_UP reads from the last
 * captured SMS onwards, for anything that arrived while the app wasn't capturing. Catch-up never
 * reaches back before capture was switched on, so skipping the history import stays skipped.
 */
class InboxImportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun runner(): CaptureRunner
        fun processor(): CaptureProcessor
    }

    override suspend fun doWork(): Result {
        if (!SmsPermissions.granted(applicationContext)) return Result.failure()
        val deps = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)

        val (uid, db) = deps.runner().currentAccount() ?: return Result.failure()
        if (uid != inputData.getString(KEY_UID)) return Result.failure()

        val now = System.currentTimeMillis()
        val since = when (inputData.getString(KEY_MODE)) {
            MODE_HISTORY -> now - Duration.ofDays(30L * inputData.getInt(KEY_MONTHS, 3)).toMillis()
            else -> catchUpSince(
                latestCaptured = db.rawCaptureDao().latestSmsTime(),
                enabledAt = inputData.getLong(KEY_ENABLED_AT, now),
            )
        }

        val messages = SmsInbox.read(applicationContext, since)
        var saved = 0
        var unparsed = 0
        messages.forEachIndexed { index, sms ->
            when (deps.processor().process(db, uid, sms).outcome) {
                CaptureOutcome.SAVED -> saved++
                CaptureOutcome.UNPARSED -> unparsed++
                else -> Unit
            }
            if (index % PROGRESS_EVERY == 0) {
                setProgress(workDataOf(KEY_DONE to index + 1, KEY_TOTAL to messages.size, KEY_SAVED to saved))
            }
        }
        return Result.success(workDataOf(KEY_SAVED to saved, KEY_UNPARSED to unparsed, KEY_TOTAL to messages.size))
    }

    companion object {
        const val KEY_UID = "uid"
        const val KEY_MODE = "mode"
        const val KEY_MONTHS = "months"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_SAVED = "saved"
        const val KEY_UNPARSED = "unparsed"
        const val KEY_ENABLED_AT = "enabled_at"

        const val MODE_HISTORY = "history"
        const val MODE_CATCH_UP = "catch_up"

        private const val PROGRESS_EVERY = 20

        private const val CATCH_UP_OVERLAP_MS = 60 * 60 * 1000L

        /**
         * Re-reads the last hour before the newest captured SMS (duplicates are ignored anyway), but
         * never anything from before the user turned capture on.
         */
        fun catchUpSince(latestCaptured: Long?, enabledAt: Long): Long =
            maxOf(latestCaptured?.minus(CATCH_UP_OVERLAP_MS) ?: enabledAt, enabledAt)

        fun input(uid: String, mode: String, months: Int = 3, enabledAt: Long = 0): Data =
            workDataOf(KEY_UID to uid, KEY_MODE to mode, KEY_MONTHS to months, KEY_ENABLED_AT to enabledAt)
    }
}
