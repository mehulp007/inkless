package com.mehul.inkless.budget

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mehul.inkless.MainActivity
import com.mehul.inkless.R
import com.mehul.inkless.capture.CaptureRunner
import com.mehul.inkless.ui.theme.NotificationStyle
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import java.time.ZoneId

/** Posts the weekly summary for whoever is signed in. */
class DigestWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun runner(): CaptureRunner
        fun tracker(): BudgetTracker
        fun scheduler(): DigestScheduler
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
        if (!deps.scheduler().enabled || !allowed()) return Result.success()
        val db = deps.runner().currentLedger() ?: return Result.success()

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val from = today.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val message = WeeklyDigest.compose(
            weekParts = db.spendDao().between(from, to),
            categories = db.categoryDao().getAll(),
            budget = deps.tracker().current(db),
        ) ?: return Result.success()

        val manager = NotificationManagerCompat.from(applicationContext)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName("Weekly summary")
                .setDescription("Sunday evening: the week's spending and what's left")
                .build(),
        )
        val open = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_inkless)
            .setColor(NotificationStyle.ACCENT)
            .setContentTitle(message.title)
            .setContentText(message.text.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Notification permission withdrawn since the check.
        }
        return Result.success()
    }

    private fun allowed() = NotificationManagerCompat.from(applicationContext).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    private companion object {
        const val CHANNEL_ID = "weekly"
        const val NOTIFICATION_ID = 7
    }
}
