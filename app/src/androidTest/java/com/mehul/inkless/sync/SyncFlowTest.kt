package com.mehul.inkless.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.firebase.firestore.FirebaseFirestoreException
import com.mehul.inkless.capture.CaptureOutcome
import com.mehul.inkless.capture.CaptureProcessor
import com.mehul.inkless.capture.IncomingSms
import com.mehul.inkless.capture.TransactionClassifier
import com.mehul.inkless.categorize.RuleField
import com.mehul.inkless.categorize.RuleMatch
import com.mehul.inkless.data.db.InklessDatabase
import com.mehul.inkless.data.db.entity.AccountMatch
import com.mehul.inkless.data.db.entity.AccountRole
import com.mehul.inkless.data.db.entity.BudgetEntity
import com.mehul.inkless.data.db.entity.BudgetPeriod
import com.mehul.inkless.data.db.entity.CaptureSource
import com.mehul.inkless.data.db.entity.Direction
import com.mehul.inkless.data.db.entity.MerchantEntity
import com.mehul.inkless.data.db.entity.MyAccountEntity
import com.mehul.inkless.data.db.entity.RuleEntity
import com.mehul.inkless.data.db.entity.SyncState
import com.mehul.inkless.data.db.entity.TransactionEntity
import com.mehul.inkless.data.db.entity.TransactionItemEntity
import com.mehul.inkless.data.db.entity.TransactionStatus
import com.mehul.inkless.data.db.entity.TransactionType
import com.mehul.inkless.data.seed.AccountSeeder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock

/** Two phones on one account, syncing through an in-memory stand-in for Firestore. */
@RunWith(AndroidJUnit4::class)
class SyncFlowTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val uid = "u1"
    private val cloud = FakeCloud()
    private lateinit var phoneA: Phone
    private lateinit var phoneB: Phone

    private inner class Phone(name: String) {
        val db: InklessDatabase = Room.inMemoryDatabaseBuilder(context, InklessDatabase::class.java).build()
        val prefs = SyncPrefs(context, "sync-test-$name").also { it.forget(uid) }
        val syncer = Syncer(cloud, prefs, Clock.systemUTC())

        suspend fun sync() = syncer.sync(uid, db)
        suspend fun pending() = db.syncDao().observePending().first()
        suspend fun transaction(id: String) = db.transactionDao().get(id)
    }

    private fun payment(id: String, updatedAt: Long = 1_000, note: String? = null) = TransactionEntity(
        id = id, amountPaise = 4500, direction = Direction.DEBIT, type = TransactionType.EXPENSE, timestamp = 1_000,
        vpa = "q829154@ybl", merchantId = "m1", categoryId = "food_canteen", status = TransactionStatus.AUTO,
        sources = setOf(CaptureSource.SMS), note = note, createdAt = 1_000, updatedAt = updatedAt, ownerUid = uid,
    )

    @Before
    fun setUp() = runTest {
        phoneA = Phone("a")
        phoneB = Phone("b")
        AccountSeeder().prepare(phoneA.db, uid)
        AccountSeeder().prepare(phoneB.db, uid)
    }

    @After
    fun tearDown() {
        phoneA.db.close()
        phoneB.db.close()
    }

    @Test
    fun aNewPhoneGetsEverythingBack() = runTest {
        val a = phoneA.db
        a.transactionDao().insert(payment("t1"))
        a.transactionDao().insert(payment("t2", note = "with Rohan"))
        a.merchantDao().insert(MerchantEntity("m1", "q829154@ybl", "Tapri", "food_canteen", 2, uid, 1_000))
        a.ruleDao().insert(RuleEntity("r1", RuleField.UPI_ID, RuleMatch.CONTAINS, "canteen", "food_canteen", 1, uid, 1_000))
        a.myAccountDao().insert(MyAccountEntity("a1", AccountMatch.NAME, "MEHUL PATIL", AccountRole.MINE, null, uid, 1_000))

        assertThat(phoneA.sync()).isEqualTo(SyncOutcome.DONE)
        assertThat(phoneA.pending()).isEqualTo(0)

        assertThat(phoneB.sync()).isEqualTo(SyncOutcome.DONE)
        val b = phoneB.db
        assertThat(b.transactionDao().count()).isEqualTo(2)
        assertThat(phoneB.transaction("t2")).isEqualTo(payment("t2", note = "with Rohan").copy(syncState = SyncState.SYNCED))
        assertThat(b.merchantDao().get("m1")?.defaultCategoryId).isEqualTo("food_canteen")
        assertThat(b.ruleDao().getAll().map { it.pattern }).containsExactly("canteen")
        assertThat(b.myAccountDao().getAll().map { it.value }).containsExactly("MEHUL PATIL")
        assertThat(b.categoryDao().getAll().map { it.id }).isEqualTo(a.categoryDao().getAll().map { it.id })
        assertThat(phoneB.pending()).isEqualTo(0)
    }

    @Test
    fun theLaterEditWinsOnBothPhones() = runTest {
        phoneA.db.transactionDao().insert(payment("t1"))
        phoneA.sync()
        phoneB.sync()

        phoneB.db.transactionDao().update(payment("t1", updatedAt = 3_000, note = "edited on B"))
        // A's edit is older (its clock is behind), so it loses even though it syncs last.
        phoneA.db.transactionDao().update(payment("t1", updatedAt = 2_000, note = "edited on A"))
        phoneB.sync()
        phoneA.sync()
        phoneB.sync()

        assertThat(phoneA.transaction("t1")?.note).isEqualTo("edited on B")
        assertThat(phoneB.transaction("t1")?.note).isEqualTo("edited on B")
    }

    @Test
    fun aDeletionReachesTheOtherPhone() = runTest {
        phoneA.db.transactionDao().insert(payment("t1"))
        phoneA.sync()
        phoneB.sync()

        phoneA.db.transactionDao().update(payment("t1", updatedAt = 2_000).copy(deleted = true))
        phoneA.sync()
        phoneB.sync()

        assertThat(phoneB.transaction("t1")?.deleted).isTrue()
    }

    @Test
    fun anEditDuringTheUploadStaysPending() = runTest {
        val dao = phoneA.db.transactionDao()
        dao.insert(payment("t1"))
        // Read for upload at updatedAt 1000, edited again before the upload finished.
        dao.update(payment("t1", updatedAt = 2_000, note = "later"))
        phoneA.db.syncDao().transactionSynced("t1", 1_000)
        assertThat(phoneA.transaction("t1")?.syncState).isEqualTo(SyncState.DIRTY)
    }

    @Test
    fun offlineLeavesEverythingPending() = runTest {
        phoneA.db.transactionDao().insert(payment("t1"))
        val before = phoneA.pending()
        cloud.online = false

        assertThat(phoneA.sync()).isEqualTo(SyncOutcome.OFFLINE)
        assertThat(phoneA.pending()).isEqualTo(before)
        assertThat(phoneA.syncer.lastProblem.value).isEqualTo(SyncOutcome.OFFLINE)
    }

    @Test
    fun switchingBackupOffDeletesTheCloudCopyAndStopsUploads() = runTest {
        phoneA.db.transactionDao().insert(payment("t1"))
        phoneA.sync()
        phoneB.sync()
        assertThat(cloud.count()).isGreaterThan(0)

        assertThat(phoneA.syncer.switchOff(uid)).isTrue()
        assertThat(cloud.count()).isEqualTo(0)

        phoneA.db.transactionDao().insert(payment("t2"))
        assertThat(phoneA.sync()).isEqualTo(SyncOutcome.BACKUP_OFF)
        // The other phone follows the switch and uploads nothing either.
        phoneB.db.transactionDao().insert(payment("t3"))
        assertThat(phoneB.sync()).isEqualTo(SyncOutcome.BACKUP_OFF)
        assertThat(phoneB.prefs.backupOn(uid)).isFalse()
        assertThat(cloud.count()).isEqualTo(0)
        // Nothing is lost on the phones.
        assertThat(phoneA.db.transactionDao().count()).isEqualTo(2)
    }

    @Test
    fun switchingBackupBackOnUploadsEverythingAgain() = runTest {
        phoneA.db.transactionDao().insert(payment("t1"))
        phoneA.sync()
        phoneA.syncer.switchOff(uid)
        phoneA.db.transactionDao().insert(payment("t2"))

        assertThat(phoneA.syncer.switchOn(uid, phoneA.db)).isEqualTo(SyncOutcome.DONE)
        assertThat(cloud.ids("transactions")).containsExactly("t1", "t2")

        phoneB.sync()
        assertThat(phoneB.db.transactionDao().count()).isEqualTo(2)
    }

    @Test
    fun theSameSmsImportedOnTheNewPhoneIsNotADuplicate() = runTest {
        val processor = CaptureProcessor(Clock.systemUTC(), TransactionClassifier())
        val sms = IncomingSms(
            sender = "VM-KOTAKB-S",
            body = "Sent Rs.45.00 from Kotak Bank AC X4321 to q829154@ybl on 06-11-26.UPI Ref 512300000501. Not you, https://kotak.com/KBANKT/Fraud",
            sentAt = 1_000,
            receivedAt = 1_000,
        )
        processor.process(phoneA.db, uid, sms)
        phoneA.sync()
        phoneB.sync()

        // The new phone's SMS inbox came back too, and its history import reads the same message.
        assertThat(processor.process(phoneB.db, uid, sms).outcome).isEqualTo(CaptureOutcome.DUPLICATE)
        assertThat(phoneB.db.transactionDao().count()).isEqualTo(1)
    }

    @Test
    fun theBudgetReachesTheNewPhone() = runTest {
        phoneA.db.budgetDao().save(BudgetEntity(BudgetEntity.MONTHLY_ID, BudgetPeriod.MONTH, 1_200_000, 5, 20_000, uid, 1_000))
        phoneA.db.categoryDao().setBudget("food_canteen", 300_000, 2_000)
        phoneA.sync()
        phoneB.sync()
        assertThat(phoneB.db.budgetDao().get()?.allowanceDay).isEqualTo(5)
        assertThat(phoneB.db.categoryDao().getAll().first { it.id == "food_canteen" }.monthlyBudgetPaise).isEqualTo(300_000)
    }

    @Test
    fun aBreakdownTravelsWithItsPayment() = runTest {
        phoneA.db.transactionDao().insert(payment("order").copy(amountPaise = 500_000))
        phoneA.db.transactionItemDao().replace(
            "order",
            listOf(
                TransactionItemEntity("i1", "order", 300_000, "shopping", "shoes", 0, uid),
                TransactionItemEntity("i2", "order", 200_000, "college", null, 1, uid),
            ),
        )
        phoneA.sync()
        phoneB.sync()
        assertThat(phoneB.db.transactionItemDao().get("order").map { it.amountPaise }).containsExactly(300_000L, 200_000L).inOrder()

        // Removed on B (a newer edit of the payment): A loses its items too.
        phoneB.db.transactionItemDao().replace("order", emptyList())
        phoneB.db.transactionDao().update(phoneB.transaction("order")!!.copy(updatedAt = 5_000, syncState = SyncState.DIRTY))
        phoneB.sync()
        phoneA.sync()
        assertThat(phoneA.db.transactionItemDao().get("order")).isEmpty()
    }
}

/** Firestore stand-in: documents per collection, stamped with a server clock that only moves forward. */
private class FakeCloud : CloudStore {
    var online = true
    private var backup = true
    private var serverClock = 1_000_000L
    private val collections = mutableMapOf<String, MutableMap<String, RemoteDoc>>()

    fun count() = collections.values.sumOf { it.size }

    fun ids(collection: String) = collections[collection].orEmpty().keys

    override suspend fun backupEnabled(uid: String): Boolean = reachable { backup }

    override suspend fun setBackupEnabled(uid: String, enabled: Boolean) = reachable { backup = enabled }

    override suspend fun write(uid: String, collection: String, docs: List<Pair<String, Map<String, Any?>>>) = reachable {
        val stored = collections.getOrPut(collection) { mutableMapOf() }
        // Like the Firestore rules: a batch with an edit older than the stored one fails as a whole.
        val stale = docs.any { (id, data) ->
            val current = stored[id]?.data?.get(CloudDocs.UPDATED_AT) as? Long ?: Long.MIN_VALUE
            (data[CloudDocs.UPDATED_AT] as Long) < current
        }
        if (stale) throw FirebaseFirestoreException("Older than the stored copy", FirebaseFirestoreException.Code.PERMISSION_DENIED)
        serverClock += 1_000
        docs.forEach { (id, data) -> stored[id] = RemoteDoc(id, data, serverClock) }
    }

    override suspend fun changedSince(uid: String, collection: String, sinceMillis: Long?): List<RemoteDoc> = reachable {
        collections[collection].orEmpty().values
            .filter { sinceMillis == null || it.syncedAt > sinceMillis }
            .sortedBy { it.syncedAt }
    }

    override suspend fun eraseAll(uid: String) = reachable { collections.clear() }

    private fun <T> reachable(block: () -> T): T {
        if (!online) throw OfflineException()
        return block()
    }
}
