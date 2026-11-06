package com.mehul.inkless.data.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Opens databases written by older versions of the app and checks nothing is lost on upgrade. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), InklessDatabase::class.java)

    @Test
    fun upgradingFromVersion1KeepsTransactionsAndAddsNewTables() = runTest {
        helper.createDatabase(dbName, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO transactions (id, amountPaise, direction, type, timestamp, confidence, status, sources,
                    tags, createdAt, updatedAt, ownerUid, deleted, syncState, categoryId, payeeRaw)
                VALUES ('t1', 4500, 'DEBIT', 'EXPENSE', 1000, 0.0, 'NEEDS_REVIEW', 'SMS',
                    '[]', 1000, 1000, 'u1', 0, 'DIRTY', 'other', 'SHARMA CANTEEN')
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(dbName, 2, true).close()
        helper.runMigrationsAndValidate(dbName, 3, true).close()
        helper.runMigrationsAndValidate(dbName, 4, true).close()
        helper.runMigrationsAndValidate(dbName, 5, true).close()
        helper.runMigrationsAndValidate(dbName, 6, true).close()

        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), InklessDatabase::class.java, dbName).build()
        try {
            val kept = db.transactionDao().get("t1")
            assertThat(kept).isNotNull()
            assertThat(kept!!.amountPaise).isEqualTo(4500)
            assertThat(kept.payeeRaw).isEqualTo("SHARMA CANTEEN")
            assertThat(db.ruleDao().getAll()).isEmpty()
            assertThat(db.myAccountDao().getAll()).isEmpty()
            assertThat(db.transactionItemDao().get("t1")).isEmpty()
            assertThat(db.budgetDao().get()).isNull()
            assertThat(db.friendDao().friends()).isEmpty()
            assertThat(db.friendDao().splitFor("t1")).isNull()
            // The old payment shows up in the new spending view.
            assertThat(db.spendDao().between(0, 2_000).map { it.amountPaise }).containsExactly(4500L)
        } finally {
            db.close()
        }
    }
}
