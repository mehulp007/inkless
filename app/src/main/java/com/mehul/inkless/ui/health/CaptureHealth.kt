package com.mehul.inkless.ui.health

import android.content.Context
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import com.mehul.inkless.capture.notification.NotificationAccess
import com.mehul.inkless.capture.sms.SmsPermissions
import com.mehul.inkless.parsing.UpiApp

/** What capture depends on, as the phone reports it right now. */
data class CaptureHealth(
    val sms: Boolean,
    val notificationAccess: Boolean,
    /** Inkless's own notifications, which ask what a new payment was. */
    val questions: Boolean,
    /** True when Android leaves Inkless alone in the background. */
    val battery: Boolean,
    val installedApps: List<String> = emptyList(),
) {
    /** Settings beyond bank SMS that would make capture smoother. */
    val toCheck: Int get() = listOf(notificationAccess, questions, battery).count { !it }

    /** "SMS is working." and what's left, for the top of Capture health and the Settings row. */
    val headline: String get() = if (sms) "SMS is working." else "SMS is off."

    val summary: String
        get() = when {
            !sms -> "Payments won't appear by themselves until SMS is allowed"
            toCheck == 0 -> "Everything capture needs is on"
            toCheck == 1 -> "1 setting can make capture smoother"
            else -> "$toCheck settings can make capture smoother"
        }

    /** The line under Capture health in Settings: "SMS active · 2 things to check". */
    val settingsLine: String
        get() {
            val sms = if (sms) "SMS active" else "SMS off"
            return when (toCheck) {
                0 -> "$sms · all good"
                1 -> "$sms · 1 thing to check"
                else -> "$sms · $toCheck things to check"
            }
        }

    companion object {
        fun read(context: Context) = CaptureHealth(
            sms = SmsPermissions.granted(context),
            notificationAccess = NotificationAccess.granted(context),
            questions = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            battery = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName),
            installedApps = UpiApp.entries.filter { isInstalled(context, it.packageName) }.map { it.label },
        )

        private fun isInstalled(context: Context, packageName: String) =
            runCatching { context.packageManager.getPackageInfo(packageName, 0) }.isSuccess
    }
}
