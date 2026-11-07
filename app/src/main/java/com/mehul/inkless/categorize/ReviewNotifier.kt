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
import com.mehul.inkless.breakdown.AfterPayment
import com.mehul.inkless.breakdown.BreakdownPrefs
import com.mehul.inkless.data.db.InklessDatabase
import com.mehul.inkless.data.db.entity.CategoryEntity
import com.mehul.inkless.data.db.entity.TransactionEntity
import com.mehul.inkless.data.repo.LedgerRepository
import com.mehul.inkless.ml.CategoryModel
import com.mehul.inkless.ui.format.Money
import com.mehul.inkless.ui.theme.NotificationStyle
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * At most one notification per freshly captured payment (see [AfterPayment]):
 * - "₹45 to SHARMA CANTEEN: what was this?" with the model's best guesses (then the user's most
 *   used categories) as buttons, and a
 *   Break down button for payments over the user's limit;
 * - "₹450 → Food Delivery" for payments over the limit that were filed on their own, with
 *   Looks right / Break down / Change.
 * Category and Looks right buttons are handled by [CategorizeReceiver] without opening the app;
 * Break down and Change open it.
 */
@Singleton
class ReviewNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: LedgerRepository,
    private val breakdownPrefs: BreakdownPrefs,
    private val model: CategoryModel,
) {
    private val manager = NotificationManagerCompat.from(context)

    fun allowed(): Boolean =
        manager.areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    /** Live captures only: history imports would flood the shade. */
    suspend fun askAbout(uid: String, db: InklessDatabase, saved: List<TransactionEntity>) {
        if (!allowed()) return
        val askAbove = breakdownPrefs.askAbovePaise(uid)
        val asks = saved.map { it to AfterPayment.of(it, askAbove) }.filter { it.second != AfterPayment.NOTHING }
        if (asks.isEmpty()) return

        val categories = db.categoryDao().getAll().associateBy { it.id }
        val picks = repository.quickPicks(MAX_BUTTONS).mapNotNull(categories::get)
        createChannels()

        for ((txn, ask) in asks) {
            val builder = when (ask) {
                AfterPayment.CHECK_IN -> checkIn(txn, txn.categoryId?.let(categories::get))
                else -> {
                    // The model's likely answers first, as long as they're at least plausible.
                    val guessed = model.guesses(db, txn.vpa, txn.payeeRaw, txn.amountPaise, txn.timestamp)
                        .filter { it.confidence >= PLAUSIBLE }
                        .mapNotNull { categories[it.categoryId] }
                    whatWasThis(txn, (guessed + picks).distinctBy { it.id }, withBreakDown = ask == AfterPayment.WHAT_WAS_THIS_OR_BREAK_DOWN)
                }
            }
            builder
                .setSmallIcon(R.drawable.ic_stat_inkless)
                .setColor(NotificationStyle.ACCENT)
                .setContentIntent(open("inkless://transaction/${txn.id}", txn.id, OPEN))
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
            post(txn.id, builder)
        }
    }

    fun dismiss(transactionId: String) = manager.cancel(TAG, transactionId.hashCode())

    private fun whatWasThis(txn: TransactionEntity, picks: List<CategoryEntity>, withBreakDown: Boolean) =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("${Money.format(txn.amountPaise)} to ${who(txn)}")
            .setContentText(if (withBreakDown) "What was this? Tap a category, or break it down." else "What was this? Tap a category.")
            .apply {
                // Android shows three buttons at most, so Break down takes the place of one category.
                picks.take(if (withBreakDown) MAX_BUTTONS - 1 else MAX_BUTTONS).forEach { category ->
                    addAction(0, category.name, CategorizeReceiver.intent(context, txn.id, category.id))
                }
                if (withBreakDown) addAction(0, "Break down", open("inkless://breakdown/${txn.id}", txn.id, BREAK_DOWN))
            }

    private fun checkIn(txn: TransactionEntity, category: CategoryEntity?) =
        NotificationCompat.Builder(context, CHECK_IN_CHANNEL_ID)
            .setContentTitle("${Money.format(txn.amountPaise)} → ${category?.name ?: "Other"}")
            .setContentText(who(txn))
            .addAction(0, "Looks right", CategorizeReceiver.confirmIntent(context, txn.id))
            .addAction(0, "Break down", open("inkless://breakdown/${txn.id}", txn.id, BREAK_DOWN))
            .addAction(0, "Change", open("inkless://transaction/${txn.id}", txn.id, CHANGE))

    private fun who(txn: TransactionEntity) = txn.payeeRaw ?: txn.vpa ?: "someone"

    private fun post(transactionId: String, builder: NotificationCompat.Builder) {
        if (!allowed()) return
        try {
            manager.notify(TAG, transactionId.hashCode(), builder.build())
        } catch (e: SecurityException) {
            // Permission withdrawn between the check and the post; nothing to do.
        }
    }

    /** Opens [link] in the app. [purpose] keeps one payment's buttons from overwriting each other. */
    private fun open(link: String, transactionId: String, purpose: Int): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, link.toUri(), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        return PendingIntent.getActivity(
            context,
            31 * transactionId.hashCode() + purpose,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun createChannels() {
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName("Payments to sort")
                .setDescription("A quick question when Inkless can't tell what a payment was")
                .build(),
        )
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHECK_IN_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName("Bigger payments")
                .setDescription("A quiet check after bigger payments, in case one was several things")
                .build(),
        )
    }

    private companion object {
        const val CHANNEL_ID = "review"
        const val CHECK_IN_CHANNEL_ID = "check_in"
        const val TAG = "review"
        // Android shows at most three buttons on a notification.
        const val MAX_BUTTONS = 3
        const val PLAUSIBLE = 0.15f

        const val OPEN = 0
        const val BREAK_DOWN = 1
        const val CHANGE = 2
    }
}
