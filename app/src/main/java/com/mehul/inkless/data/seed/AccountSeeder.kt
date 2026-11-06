package com.mehul.inkless.data.seed

import com.mehul.inkless.data.db.InklessDatabase
import java.time.Clock
import javax.inject.Inject

/** Gives a brand-new account its default categories. */
class AccountSeeder @Inject constructor(
    private val clock: Clock,
) {
    suspend fun prepare(db: InklessDatabase, ownerUid: String) {
        if (db.categoryDao().count() > 0) return
        db.categoryDao().insertAll(DefaultCategories.create(ownerUid, clock.millis()))
    }
}
