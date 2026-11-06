package com.mehul.inkless.export

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mehul.inkless.data.db.InklessDatabase
import com.mehul.inkless.data.db.entity.CaptureSource
import com.mehul.inkless.data.db.entity.Direction
import com.mehul.inkless.data.db.entity.FriendEntity
import com.mehul.inkless.data.db.entity.SplitEntity
import com.mehul.inkless.data.db.entity.SplitMethod
import com.mehul.inkless.data.db.entity.SplitShareEntity
import com.mehul.inkless.data.db.entity.SyncState
import com.mehul.inkless.data.db.entity.TransactionEntity
import com.mehul.inkless.data.db.entity.TransactionItemEntity
import com.mehul.inkless.data.db.entity.TransactionStatus
import com.mehul.inkless.data.db.entity.TransactionType
import com.mehul.inkless.data.seed.AccountSeeder
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock

/** A backup file written on one phone, restored on another (and into another account). */
@RunWith(AndroidJUnit4::class)
class BackupRestoreTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val exporter = DataExporter(context, Clock.systemUTC())
    private lateinit var oldPhone: InklessDatabase
    private lateinit var newPhone: InklessDatabase

    private fun payment(id: String, note: String? = null, updatedAt: Long = 1_000) = TransactionEntity(
        id = id, amountPaise = 90_000, direction = Direction.DEBIT, type = TransactionType.EXPENSE, timestamp = 1_000,
        payeeRaw = "Swiggy", categoryId = "food_delivery", status = TransactionStatus.CONFIRMED,
        sources = setOf(CaptureSource.SMS), note = note, createdAt = 1_000, updatedAt = updatedAt, ownerUid = "old",
    )

    @Before
    fun setUp() = runTest {
        oldPhone = Room.inMemoryDatabaseBuilder(context, InklessDatabase::class.java).build()
        newPhone = Room.inMemoryDatabaseBuilder(context, InklessDatabase::class.java).build()
        AccountSeeder().prepare(oldPhone, "old")
        AccountSeeder().prepare(newPhone, "new")
    }

    @After
    fun tearDown() {
        oldPhone.close()
        newPhone.close()
    }

    @Test
    fun everythingComesBackUnderTheNewAccountAndQueuedForTheCloud() = runTest {
        oldPhone.transactionDao().insertAll(listOf(payment("dinner"), payment("order")))
        oldPhone.transactionItemDao().replace(
            "order",
            listOf(
                TransactionItemEntity("i1", "order", 50_000, "food_delivery", null, 0, "old"),
                TransactionItemEntity("i2", "order", 40_000, "groceries", null, 1, "old"),
            ),
        )
        oldPhone.friendDao().saveFriend(FriendEntity("f1", "Rohan", ownerUid = "old", updatedAt = 1_000))
        oldPhone.friendDao().saveSplit(SplitEntity("s1", "dinner", SplitMethod.EQUAL, ownerUid = "old", updatedAt = 1_000))
        oldPhone.friendDao().replaceShares("s1", listOf(SplitShareEntity("sh1", "s1", "f1", 45_000, ownerUid = "old")))

        val file = exporter.encrypt(exporter.json(oldPhone), "backup-pw-1".toCharArray())
        val result = exporter.restore(newPhone, file, "backup-pw-1".toCharArray(), ownerUid = "new")

        assertThat(result.payments).isEqualTo(2)
        val restored = newPhone.transactionDao().get("dinner")!!
        assertThat(restored.ownerUid).isEqualTo("new")
        assertThat(restored.syncState).isEqualTo(SyncState.DIRTY)
        assertThat(newPhone.transactionItemDao().get("order").map { it.amountPaise }).containsExactly(50_000L, 40_000L).inOrder()
        assertThat(newPhone.friendDao().shares("s1").single().sharePaise).isEqualTo(45_000)
        // Only the user's part of the split dinner counts, as before.
        assertThat(newPhone.spendDao().between(0, 2_000).sumOf { it.amountPaise }).isEqualTo(45_000 + 90_000)
    }

    @Test
    fun aLaterEditOnThePhoneIsKept() = runTest {
        oldPhone.transactionDao().insert(payment("dinner", note = "old note", updatedAt = 1_000))
        newPhone.transactionDao().insert(payment("dinner", note = "edited since", updatedAt = 5_000))
        val result = exporter.restore(newPhone, exporter.json(oldPhone).toByteArray(), null, ownerUid = "new")
        assertThat(result.payments).isEqualTo(0)
        assertThat(newPhone.transactionDao().get("dinner")?.note).isEqualTo("edited since")
    }
}
