package com.mehul.inkless.data.seed

import com.mehul.inkless.data.db.InklessDatabase
import javax.inject.Inject

/** Gives a brand-new account its default categories. */
class AccountSeeder @Inject constructor() {
    suspend fun prepare(db: InklessDatabase, ownerUid: String) {
        if (db.categoryDao().count() > 0) return
        // Dated at the very beginning, so on a new phone any change restored from the backup wins.
        db.categoryDao().insertAll(DefaultCategories.create(ownerUid, nowMillis = 0L))
    }
}
