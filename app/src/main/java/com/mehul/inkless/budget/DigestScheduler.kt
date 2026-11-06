package com.mehul.inkless.budget

import android.content.Context
import androidx.core.content.edit
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton

/** Keeps the weekly summary booked for Sunday evenings, or not, as the user chose. */
@Singleton
class DigestScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val clock: Clock,
) {
    private val prefs = context.getSharedPreferences("digest", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) {
            prefs.edit { putBoolean(KEY_ENABLED, value) }
            apply()
        }

    fun apply() {
        val work = WorkManager.getInstance(context)
        if (!enabled) {
            work.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<DigestWorker>(Duration.ofDays(7))
            .setInitialDelay(untilNextSundayEvening(ZonedDateTime.now(clock)))
            .build()
        work.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val WORK_NAME = "weekly-digest"
        private val SEND_AT: LocalTime = LocalTime.of(19, 0)

        fun untilNextSundayEvening(now: ZonedDateTime): Duration {
            var next = now.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).with(SEND_AT)
            if (!next.isAfter(now)) next = next.plusWeeks(1)
            return Duration.between(now, next)
        }
    }
}
