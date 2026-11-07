package com.mehul.inkless.splits

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mehul.inkless.MainActivity
import com.mehul.inkless.R
import com.mehul.inkless.capture.CaptureRunner
import com.mehul.inkless.categorize.ReviewNotifier
import com.mehul.inkless.data.db.InklessDatabase
import com.mehul.inkless.data.db.dao.ShareLine
import com.mehul.inkless.data.db.entity.Direction
import com.mehul.inkless.data.db.entity.TransactionEntity
import com.mehul.inkless.di.ApplicationScope
import com.mehul.inkless.ui.format.Money
import com.mehul.inkless.ui.theme.NotificationStyle
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * When money arrives from a friend who owes the user, asks whether it settles what they owe:
 * "₹150 from Rohan: Rohan's share of Swiggy · 3 Nov?" with a button that marks it paid back.
 */
@Singleton
class SettleSuggester @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val reviewNotifier: ReviewNotifier,
) {
    private val manager = NotificationManagerCompat.from(context)

    /** Live captures only, like the other payment questions. */
    suspend fun suggest(db: InklessDatabase, saved: List<TransactionEntity>) {
        val credits = saved.filter { it.direction == Direction.CREDIT }
        if (credits.isEmpty() || !reviewNotifier.allowed()) return
        val dao = db.friendDao()
        val friends = dao.friends()
        for (credit in credits) {
            val friend = SettleMatch.friendFor(credit, friends) ?: continue
            val targets = SettleMatch.targets(credit.amountPaise, dao.openShares(friend.id), dao.openIous(friend.id)) ?: continue
            post(credit, friend.name, targets)
        }
    }

    private fun post(credit: TransactionEntity, name: String, targets: List<SettleTarget>) {
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName("Friends paying back")
                .setDescription("When money from a friend looks like what they owe you")
                .build(),
        )
        val what = targets.singleOrNull()?.let { describe(name, it) } ?: "Everything $name owes you?"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_inkless)
            .setColor(NotificationStyle.ACCENT)
            .setContentTitle("${Money.format(credit.amountPaise)} from $name")
            .setContentText(what)
            .addAction(0, "Mark paid back", SettleReceiver.intent(context, credit.id, targets))
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    credit.id.hashCode(),
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(TAG, credit.id.hashCode(), notification)
        } catch (e: SecurityException) {
            // Notification permission withdrawn since the check.
        }
    }

    fun dismiss(creditId: String) = manager.cancel(TAG, creditId.hashCode())

    private fun describe(name: String, target: SettleTarget): String = when (target) {
        is SettleTarget.Share -> "$name's share of ${target.line.title ?: "a payment"} · ${day(target.line.timestamp)}?"
        is SettleTarget.Iou -> "The ${Money.format(target.iou.amountPaise)} $name owed you" + (target.iou.note?.let { " for $it" } ?: "") + "?"
    }

    private fun day(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DAY)

    private companion object {
        const val CHANNEL_ID = "settle"
        const val TAG = "settle"
        val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
    }
}

/** "Mark paid back" on the settle notification, handled without opening the app. */
@AndroidEntryPoint
class SettleReceiver : BroadcastReceiver() {

    @Inject lateinit var runner: CaptureRunner

    @Inject lateinit var friends: FriendsRepository

    @Inject lateinit var suggester: SettleSuggester

    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val creditId = intent.getStringExtra(EXTRA_CREDIT) ?: return
        val shareIds = intent.getStringArrayExtra(EXTRA_SHARES).orEmpty()
        val iouIds = intent.getStringArrayExtra(EXTRA_IOUS).orEmpty()
        val pending = goAsync()
        scope.launch {
            try {
                val (_, db) = runner.currentAccount() ?: return@launch
                val dao = db.friendDao()
                val targets = shareIds.mapNotNull { id ->
                    dao.share(id)?.let { SettleTarget.Share(ShareLine(it.id, it.splitId, it.friendId, it.sharePaise, it.settled, "", 0, null)) }
                } + iouIds.mapNotNull { id -> dao.iou(id)?.let { SettleTarget.Iou(it) } }
                friends.settle(targets, byTransactionId = creditId)
                suggester.dismiss(creditId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val EXTRA_CREDIT = "credit"
        private const val EXTRA_SHARES = "shares"
        private const val EXTRA_IOUS = "ious"

        fun intent(context: Context, creditId: String, targets: List<SettleTarget>): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                creditId.hashCode(),
                Intent(context, SettleReceiver::class.java)
                    .putExtra(EXTRA_CREDIT, creditId)
                    .putExtra(EXTRA_SHARES, targets.filterIsInstance<SettleTarget.Share>().map { it.line.shareId }.toTypedArray())
                    .putExtra(EXTRA_IOUS, targets.filterIsInstance<SettleTarget.Iou>().map { it.iou.id }.toTypedArray()),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}
