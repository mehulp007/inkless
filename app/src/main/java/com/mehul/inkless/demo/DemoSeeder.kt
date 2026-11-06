package com.mehul.inkless.demo

import androidx.room.withTransaction
import com.mehul.inkless.data.db.InklessDatabase
import com.mehul.inkless.data.db.entity.BudgetEntity
import com.mehul.inkless.data.db.entity.BudgetPeriod
import com.mehul.inkless.data.db.entity.Direction
import com.mehul.inkless.data.db.entity.FriendEntity
import com.mehul.inkless.data.db.entity.IouEntity
import com.mehul.inkless.data.db.entity.SplitEntity
import com.mehul.inkless.data.db.entity.SplitMethod
import com.mehul.inkless.data.db.entity.SplitShareEntity
import com.mehul.inkless.data.db.entity.TransactionEntity
import com.mehul.inkless.data.seed.CategoryIds
import com.mehul.inkless.data.seed.DefaultCategories
import com.mehul.inkless.splits.SplitMath
import java.time.Clock
import java.time.Duration
import java.time.ZonedDateTime
import javax.inject.Inject

class DemoSeeder @Inject constructor(
    private val clock: Clock,
) {
    /**
     * Fills the demo database. If the data has gone stale (app not opened for a while) it is thrown
     * away and regenerated, so the current month is never empty in screenshots.
     */
    suspend fun prepare(db: InklessDatabase) {
        val now = ZonedDateTime.now(clock)
        val nowMillis = now.toInstant().toEpochMilli()

        db.withTransaction {
            if (db.categoryDao().count() == 0) {
                db.categoryDao().insertAll(DefaultCategories.create(Demo.OWNER_UID, nowMillis))
            }
            // The demo's allowance arrives on the 1st, like the generated one.
            if (db.budgetDao().get() == null) {
                db.budgetDao().save(
                    BudgetEntity(
                        id = BudgetEntity.MONTHLY_ID,
                        period = BudgetPeriod.MONTH,
                        amountPaise = 12_000_00,
                        allowanceDay = 1,
                        dailyFoodCapPaise = 200_00,
                        ownerUid = Demo.OWNER_UID,
                        updatedAt = nowMillis,
                    ),
                )
            }

            val latest = db.transactionDao().latestTimestamp()
            if (latest != null && nowMillis - latest < STALE_AFTER.toMillis()) return@withTransaction

            val data = DemoDataGenerator().generate(now)
            db.transactionDao().deleteAll()
            db.merchantDao().deleteAll()
            db.merchantDao().insertAll(data.merchants)
            db.transactionDao().insertAll(data.transactions)
            seedFriends(db, data.transactions, nowMillis)
        }
    }

    /** Two friends, the latest big food order split three ways, and some money lent. */
    private suspend fun seedFriends(db: InklessDatabase, transactions: List<TransactionEntity>, nowMillis: Long) {
        val dao = db.friendDao()
        val rohan = FriendEntity(id = "demo-rohan", name = "Rohan Mehta", vpas = listOf("rohan.mehta@okicici"), ownerUid = Demo.OWNER_UID, updatedAt = nowMillis)
        val priya = FriendEntity(id = "demo-priya", name = "Priya Nair", vpas = listOf("priya.n@ybl"), ownerUid = Demo.OWNER_UID, updatedAt = nowMillis)
        dao.saveFriend(rohan)
        dao.saveFriend(priya)

        val order = transactions
            .filter { it.categoryId == CategoryIds.FOOD_DELIVERY && it.direction == Direction.DEBIT && it.amountPaise >= 300_00 }
            .maxByOrNull { it.timestamp }
        if (order != null) {
            val split = SplitEntity(id = "demo-split", transactionId = order.id, method = SplitMethod.EQUAL, ownerUid = Demo.OWNER_UID, updatedAt = nowMillis)
            dao.saveSplit(split)
            dao.replaceShares(
                split.id,
                SplitMath.equal(order.amountPaise, listOf(rohan.id, priya.id)).mapIndexed { index, share ->
                    // Priya has already paid her part back.
                    SplitShareEntity(
                        id = "demo-share-$index",
                        splitId = split.id,
                        friendId = share.friendId,
                        sharePaise = share.sharePaise,
                        settled = share.friendId == priya.id,
                        ownerUid = Demo.OWNER_UID,
                    )
                },
            )
        }
        if (dao.iou("demo-iou") == null) {
            dao.saveIou(
                IouEntity(
                    id = "demo-iou",
                    friendId = rohan.id,
                    amountPaise = 150_00,
                    theyOwe = true,
                    note = "Auto to the station",
                    timestamp = nowMillis - Duration.ofDays(2).toMillis(),
                    ownerUid = Demo.OWNER_UID,
                    updatedAt = nowMillis,
                ),
            )
        }
    }

    private companion object {
        val STALE_AFTER: Duration = Duration.ofHours(24)
    }
}
