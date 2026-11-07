package com.mehul.inkless.insights

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import com.mehul.inkless.MainActivity
import com.mehul.inkless.R
import com.mehul.inkless.categorize.ReviewNotifier
import com.mehul.inkless.data.db.InklessDatabase
import com.mehul.inkless.data.db.entity.Direction
import com.mehul.inkless.data.db.entity.TransactionEntity
import com.mehul.inkless.ui.format.Money
import com.mehul.inkless.ui.theme.NotificationStyle
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Words for an alert: "🍜 Food Delivery: ₹1,240 this week" / "About 2.3× a usual week (₹540)." */
object AlertText {

    fun title(alert: SpendAlert, category: String): String =
        "$category: ${Money.format(alert.spentPaise)} ${if (alert.period == AlertPeriod.WEEK) "this week" else "today"}"

    fun body(alert: SpendAlert): String {
        val usualWhat = if (alert.period == AlertPeriod.WEEK) "a usual week" else "a usual day"
        val times = (alert.times * 10).toInt().let { "${it / 10}.${it % 10}" }.removeSuffix(".0")
        return "About $times× $usualWhat (${Money.format(alert.usualPaise)})."
    }
}

/**
 * Spending alerts: checked after each live payment, and at most one notification a day, on a
 * quiet channel of its own. The Insights screen lists every alert that applies right now.
 */
@Singleton
class SpendAlerts @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val reviewNotifier: ReviewNotifier,
    private val clock: Clock,
) {
    private val manager = NotificationManagerCompat.from(context)
    private val prefs = context.getSharedPreferences("spend_alerts", Context.MODE_PRIVATE)

    suspend fun current(db: InklessDatabase): List<SpendAlert> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(clock.withZone(zone))
        val from = today.minusWeeks(Anomalies.WEEKS_BACK + 1L).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return Anomalies.find(db.spendDao().between(from, to), today, zone)
    }

    suspend fun check(uid: String, db: InklessDatabase, saved: List<TransactionEntity>) {
        if (saved.none { it.direction == Direction.DEBIT } || !reviewNotifier.allowed()) return
        val today = LocalDate.now(clock.withZone(ZoneId.systemDefault())).toString()
        if (prefs.getString("$uid:day", null) == today) return
        val sent = prefs.getStringSet("$uid:sent", emptySet()).orEmpty()
        val alerts = current(db)
        val fresh = alerts.firstOrNull { it.key !in sent } ?: return

        val category = db.categoryDao().getAll().firstOrNull { it.id == fresh.categoryId }?.name ?: "Spending"
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName("Spending alerts")
                .setDescription("When a category is well above its usual, at most once a day")
                .build(),
        )
        val open = PendingIntent.getActivity(
            context,
            CHANNEL_ID.hashCode(),
            Intent(Intent.ACTION_VIEW, "inkless://insights".toUri(), context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_inkless)
            .setColor(NotificationStyle.ACCENT)
            .setContentTitle(AlertText.title(fresh, category))
            .setContentText(AlertText.body(fresh))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(CHANNEL_ID, 0, notification)
        } catch (e: SecurityException) {
            return
        }
        prefs.edit {
            putString("$uid:day", today)
            // Everything showing now counts as told, so tomorrow's alert is about something new.
            putStringSet("$uid:sent", (sent + alerts.map { it.key }).toList().takeLast(KEEP).toSet())
        }
    }

    private companion object {
        const val CHANNEL_ID = "spend_alerts"
        const val KEEP = 60
    }
}
