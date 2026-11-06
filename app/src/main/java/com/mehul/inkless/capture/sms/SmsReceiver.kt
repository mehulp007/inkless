package com.mehul.inkless.capture.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.mehul.inkless.capture.CaptureRunner
import com.mehul.inkless.capture.IncomingSms
import com.mehul.inkless.categorize.ReviewNotifier
import com.mehul.inkless.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Android calls this for every incoming SMS (it's registered for SMS_RECEIVED in the manifest).
 * Long messages arrive in parts, so parts from the same sender are joined before parsing.
 */
@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    @Inject lateinit var runner: CaptureRunner

    @Inject lateinit var notifier: ReviewNotifier

    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val now = System.currentTimeMillis()

        val messages = parts.filterNotNull()
            .groupBy { it.originatingAddress.orEmpty() }
            .map { (sender, pieces) ->
                IncomingSms(
                    sender = sender,
                    body = pieces.joinToString("") { it.messageBody.orEmpty() },
                    sentAt = pieces.first().timestampMillis,
                    receivedAt = now,
                )
            }

        // goAsync keeps the receiver alive while the database work finishes in the background.
        val pending = goAsync()
        scope.launch {
            try {
                val saved = runner.capture(messages)
                // Only live payments get a question; history imports would flood the shade.
                runner.currentAccount()?.let { (uid, db) -> notifier.askAbout(uid, db, saved) }
            } finally {
                pending.finish()
            }
        }
    }
}
