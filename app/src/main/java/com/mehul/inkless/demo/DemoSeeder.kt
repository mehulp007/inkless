package com.mehul.inkless.demo

import androidx.room.withTransaction
import com.mehul.inkless.data.db.InklessDatabase
import com.mehul.inkless.data.db.entity.BudgetEntity
import com.mehul.inkless.data.db.entity.BudgetPeriod
import com.mehul.inkless.data.seed.DefaultCategories
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
        }
    }

    private companion object {
        val STALE_AFTER: Duration = Duration.ofHours(24)
    }
}
