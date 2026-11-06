package com.mehul.inkless.ml

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mehul.inkless.capture.CaptureProcessor
import com.mehul.inkless.capture.IncomingSms
import com.mehul.inkless.capture.TransactionClassifier
import com.mehul.inkless.data.db.DatabaseProvider
import com.mehul.inkless.data.db.InklessDatabase
import com.mehul.inkless.data.db.entity.CaptureSource
import com.mehul.inkless.data.db.entity.Direction
import com.mehul.inkless.data.db.entity.PredictionSource
import com.mehul.inkless.data.db.entity.TransactionEntity
import com.mehul.inkless.data.db.entity.TransactionItemEntity
import com.mehul.inkless.data.db.entity.TransactionStatus
import com.mehul.inkless.data.db.entity.TransactionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId

/** The learned model in the capture pipeline, on a real (in-memory) database. */
@RunWith(AndroidJUnit4::class)
class ModelFlowTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: InklessDatabase
    private lateinit var model: CategoryModel
    private lateinit var processor: CaptureProcessor

    // Evenings, in the phone's own time zone, which is what the model's time-of-day uses.
    private fun evening(day: Int) = LocalDateTime.of(2026, 10, day, 17, 30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun sorted(id: String, payee: String, rupees: Long, category: String, day: Int, status: TransactionStatus = TransactionStatus.CONFIRMED) =
        TransactionEntity(
            id = id, amountPaise = rupees * 100, direction = Direction.DEBIT, type = TransactionType.EXPENSE,
            timestamp = evening(day), payeeRaw = payee, categoryId = category, status = status,
            sources = setOf(CaptureSource.SMS), createdAt = 0, updatedAt = 0, ownerUid = "u1",
        )

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, InklessDatabase::class.java).build()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        model = CategoryModel(DatabaseProvider(context, scope), scope)
        processor = CaptureProcessor(Clock.systemUTC(), TransactionClassifier(), model)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun retrain() = model.retrain(db, db.insightDao().observeTrainingRows().first())

    @Test
    fun aTrainedModelFilesANewStallAndLogsItsGuess() = runTest {
        val stalls = listOf("GUPTA STALL", "BHOLA STALL", "MAIN GATE STALL", "HOSTEL STALL")
        val all = (1..24).map { sorted("s$it", stalls[it % stalls.size], 20L + it % 3 * 5, "food_canteen", 1 + it % 28) } +
            (1..6).map { sorted("x$it", "PRINT POINT $it", 300, "college", 1 + it) }
        db.transactionDao().insertAll(all)
        retrain()
        assertThat(model.of(db).size).isEqualTo(30)

        val sms = IncomingSms(
            sender = "VM-SBIUPI-S",
            body = "Dear UPI user A/C X8813 debited by 25.00 on date 06Nov26 trf to NEW BLOCK STALL Refno 497500000099 If not u? call-1800111109-SBI",
            sentAt = evening(30),
            receivedAt = evening(30),
        )
        val saved = processor.process(db, "u1", sms).transaction!!
        assertThat(saved.categoryId).isEqualTo("food_canteen")
        assertThat(saved.status).isEqualTo(TransactionStatus.AUTO)

        val outcome = db.insightDao().observeOutcomes().first().single()
        assertThat(outcome.source).isEqualTo(PredictionSource.MODEL)
        assertThat(outcome.modelCategoryId).isEqualTo("food_canteen")

        // Its own guess isn't something to learn from, until the user confirms it.
        assertThat(db.insightDao().observeTrainingRows().first()).hasSize(30)
        db.transactionDao().update(saved.copy(status = TransactionStatus.CONFIRMED))
        assertThat(db.insightDao().observeTrainingRows().first()).hasSize(31)
    }

    @Test
    fun brokenDownAndUnsortedPaymentsAreNotLearnedFrom() = runTest {
        db.transactionDao().insertAll(
            listOf(
                sorted("a", "AMAZON", 5_000, "shopping", 3),
                sorted("b", "RAMU", 30, "other", 4, TransactionStatus.NEEDS_REVIEW),
                sorted("c", "CANTEEN", 40, "food_canteen", 5, TransactionStatus.AUTO),
                sorted("d", "ME", 2_000, "other", 6).copy(type = TransactionType.SELF_TRANSFER),
            ),
        )
        db.transactionItemDao().replace(
            "a",
            listOf(
                TransactionItemEntity("i1", "a", 3_000_00, "shopping", null, 0, "u1"),
                TransactionItemEntity("i2", "a", 2_000_00, "college", null, 1, "u1"),
            ),
        )
        assertThat(db.insightDao().observeTrainingRows().first().map { it.payeeRaw }).containsExactly("CANTEEN")
    }
}
