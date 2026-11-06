package com.mehul.inkless.capture.notification

import android.app.Notification
import android.content.Context
import android.content.pm.ApplicationInfo
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import com.mehul.inkless.capture.CaptureRunner
import com.mehul.inkless.capture.IncomingNotification
import com.mehul.inkless.categorize.ReviewNotifier
import com.mehul.inkless.di.ApplicationScope
import com.mehul.inkless.parsing.UpiApp
import com.mehul.inkless.insights.SpendAlerts
import com.mehul.inkless.splits.SettleSuggester
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Once the user grants notification access, Android shows this service every notification on
 * the phone. It looks only at UPI apps' ones and ignores everything else without reading it.
 */
@AndroidEntryPoint
class UpiNotificationListener : NotificationListenerService() {

    @Inject lateinit var runner: CaptureRunner

    @Inject lateinit var notifier: ReviewNotifier

    @Inject lateinit var settle: SettleSuggester

    @Inject lateinit var alerts: SpendAlerts

    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!NotificationAccess.isWatched(this, sbn.packageName)) return
        val notification = sbn.notification
        // Grouped notifications post a summary as well; the individual ones carry the details.
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString().orEmpty()
        val text = "$title\n$body".trim()
        if (text.isEmpty()) return

        val incoming = IncomingNotification(sbn.packageName, sbn.key, text, sbn.postTime)
        scope.launch {
            val saved = runner.capture(incoming) ?: return@launch
            runner.currentAccount()?.let { (uid, db) ->
                notifier.askAbout(uid, db, listOf(saved))
                settle.suggest(db, listOf(saved))
                alerts.check(uid, db, listOf(saved))
            }
        }
    }
}

object NotificationAccess {

    // Debug builds also listen to notifications posted from the shell (`adb shell cmd notification post`),
    // which is how the listener is tested on an emulator without real UPI apps.
    private const val SHELL = "com.android.shell"

    fun isWatched(context: Context, packageName: String): Boolean =
        UpiApp.of(packageName) != null || (packageName == SHELL && isDebuggable(context))

    /** Whether the user has turned on notification access for Inkless in system settings. */
    fun granted(context: Context): Boolean =
        context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

    private fun isDebuggable(context: Context) =
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
}
