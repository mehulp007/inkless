package com.mehul.inkless.categorize

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mehul.inkless.capture.CaptureRunner
import com.mehul.inkless.data.repo.LedgerRepository
import com.mehul.inkless.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Handles the category and "Looks right" buttons on payment notifications, without opening the app. */
@AndroidEntryPoint
class CategorizeReceiver : BroadcastReceiver() {

    @Inject lateinit var runner: CaptureRunner

    @Inject lateinit var repository: LedgerRepository

    @Inject lateinit var notifier: ReviewNotifier

    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val transactionId = intent.getStringExtra(EXTRA_TRANSACTION) ?: return
        // No category means "Looks right": keep the category Inkless picked.
        val categoryId = intent.getStringExtra(EXTRA_CATEGORY)

        val pending = goAsync()
        scope.launch {
            try {
                // The app may have been closed; wait for the signed-in account's database to open.
                if (runner.currentAccount() != null) {
                    if (categoryId != null) repository.categorize(transactionId, categoryId) else repository.keepAsIs(transactionId)
                }
                notifier.dismiss(transactionId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val EXTRA_TRANSACTION = "transaction"
        private const val EXTRA_CATEGORY = "category"

        fun intent(context: Context, transactionId: String, categoryId: String): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                "$transactionId:$categoryId".hashCode(),
                Intent(context, CategorizeReceiver::class.java)
                    .putExtra(EXTRA_TRANSACTION, transactionId)
                    .putExtra(EXTRA_CATEGORY, categoryId),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        fun confirmIntent(context: Context, transactionId: String): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                "$transactionId:ok".hashCode(),
                Intent(context, CategorizeReceiver::class.java).putExtra(EXTRA_TRANSACTION, transactionId),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}
