package com.mehul.inkless.categorize

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
import androidx.core.net.toUri
import com.mehul.inkless.MainActivity
import com.mehul.inkless.R
import com.mehul.inkless.data.db.InklessDatabase
import com.mehul.inkless.data.db.entity.TransactionEntity
import com.mehul.inkless.data.db.entity.TransactionStatus
import com.mehul.inkless.data.db.entity.TransactionType
import com.mehul.inkless.data.repo.LedgerRepository
import com.mehul.inkless.ui.format.Money
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "₹45 to SHARMA CANTEEN — what was this?" with the user's top categories as buttons. Tapping a
 * button is handled by [CategorizeReceiver] without opening the app; tapping the notification
 * opens the payment to pick any other category.
 */
@Singleton
class ReviewNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: LedgerRepository,
) {
    private val manager = NotificationManagerCompat.from(context)

    fun allowed(): Boolean =
        manager.areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    /** Asks about each freshly captured payment that couldn't be filed on its own. */
    suspend fun askAbout(db: InklessDatabase, saved: List<TransactionEntity>) {
        val unsure = saved.filter { it.type == TransactionType.EXPENSE && it.status == TransactionStatus.NEEDS_REVIEW }
        if (unsure.isEmpty() || !allowed()) return

        val categories = db.categoryDao().getAll().associateBy { it.id }
        val picks = repository.quickPicks(MAX_BUTTONS).mapNotNull(categories::get)
        createChannel()

        for (txn in unsure) {
            val who = txn.payeeRaw ?: txn.vpa ?: "someone"
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_inkless)
                .setContentTitle("${Money.format(txn.amountPaise)} to $who")
                .setContentText("What was this? Tap a category.")
                .setContentIntent(openPayment(txn.id))
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
            picks.forEach { category ->
                builder.addAction(0, "${category.emoji} ${category.name}", CategorizeReceiver.intent(context, txn.id, category.id))
            }
            post(txn.id, builder)
        }
    }

    fun dismiss(transactionId: String) = manager.cancel(TAG, transactionId.hashCode())

    private fun post(transactionId: String, builder: NotificationCompat.Builder) {
        if (!allowed()) return
        try {
            manager.notify(TAG, transactionId.hashCode(), builder.build())
        } catch (e: SecurityException) {
            // Permission withdrawn between the check and the post; nothing to do.
        }
    }

    private fun openPayment(transactionId: String): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, "inkless://transaction/$transactionId".toUri(), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        return PendingIntent.getActivity(
            context,
            transactionId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun createChannel() {
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName("Payments to sort")
                .setDescription("A quick question when Inkless can't tell what a payment was")
                .build(),
        )
    }

    private companion object {
        const val CHANNEL_ID = "review"
        const val TAG = "review"
        // Android shows at most three buttons on a notification.
        const val MAX_BUTTONS = 3
    }
}
