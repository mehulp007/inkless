package com.mehul.inkless.categorize

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mehul.inkless.capture.CaptureProcessor
import com.mehul.inkless.capture.IncomingSms
import com.mehul.inkless.data.db.InklessDatabase
import com.mehul.inkless.data.db.entity.CaptureSource
import com.mehul.inkless.data.db.entity.Direction
import com.mehul.inkless.data.db.entity.RuleEntity
import com.mehul.inkless.data.db.entity.TransactionEntity
import com.mehul.inkless.data.db.entity.TransactionStatus
import com.mehul.inkless.data.db.entity.TransactionType
import com.mehul.inkless.data.seed.CategoryIds
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class CategorizeFlowTest {

    private lateinit var db: InklessDatabase
    private val clock = Clock.fixed(Instant.parse("2026-11-06T10:00:00Z"), ZoneOffset.UTC)
    private val processor = CaptureProcessor(clock)

    // SBI names the payee but gives no UPI ID, and nothing in the name says what it is.
    private fun sbi(amount: String, ref: String, sentAt: Long) = IncomingSms(
        sender = "VM-SBIUPI-S",
        body = "Dear UPI user A/C X8813 debited by $amount on date 06Nov26 trf to RAMU BHAIYA Refno $ref If not u? call-1800111109-SBI",
        sentAt = sentAt,
        receivedAt = sentAt,
    )

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), InklessDatabase::class.java).build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun secondPaymentToTheSamePlaceIsFiledAutomatically() = runTest {
        val first = processor.process(db, "u1", sbi("30.00", "497500000001", 1_000L)).transaction!!
        assertThat(first.status).isEqualTo(TransactionStatus.NEEDS_REVIEW)
        assertThat(first.categoryId).isEqualTo(CategoryIds.OTHER)

        // The user says what it was, once.
        MerchantMemory.learn(db, first.merchantId!!, CategoryIds.FOOD_CANTEEN, 2_000L)

        val second = processor.process(db, "u1", sbi("25.00", "497500000002", 3_000L)).transaction!!
        assertThat(second.status).isEqualTo(TransactionStatus.AUTO)
        assertThat(second.categoryId).isEqualTo(CategoryIds.FOOD_CANTEEN)
        assertThat(second.merchantId).isEqualTo(first.merchantId)
    }

    @Test
    fun learningAMerchantFilesItsOtherWaitingPayments() = runTest {
        val a = processor.process(db, "u1", sbi("30.00", "497500000011", 1_000L)).transaction!!
        val b = processor.process(db, "u1", sbi("40.00", "497500000012", 2_000L)).transaction!!

        val filed = MerchantMemory.learn(db, a.merchantId!!, CategoryIds.FOOD_CANTEEN, 5_000L)

        assertThat(filed).isEqualTo(2)
        assertThat(db.transactionDao().get(b.id)!!.categoryId).isEqualTo(CategoryIds.FOOD_CANTEEN)
    }

    @Test
    fun keywordsFileKnownMerchantsStraightAway() = runTest {
        val sms = IncomingSms(
            sender = "VM-KOTAKB",
            body = "Sent Rs.249.00 from Kotak Bank AC X1234 to swiggy@icici on 06-11-26.UPI Ref 512345678904. Not you, https://kotak.com/KBANKT/Fraud",
            sentAt = 1_000L,
            receivedAt = 1_000L,
        )
        val txn = processor.process(db, "u1", sms).transaction!!
        assertThat(txn.categoryId).isEqualTo(CategoryIds.FOOD_DELIVERY)
        assertThat(txn.status).isEqualTo(TransactionStatus.AUTO)
    }

    @Test
    fun aRuleBeatsTheKeywordGuess() = runTest {
        db.ruleDao().insert(
            RuleEntity("r1", RuleField.UPI_ID, RuleMatch.CONTAINS, "swiggy", CategoryIds.FUN, 1, "u1", 0L),
        )
        val sms = IncomingSms("VM-KOTAKB", "Sent Rs.99.00 from Kotak Bank AC X1234 to swiggy@icici on 06-11-26.UPI Ref 512345678905.", 1_000L, 1_000L)
        assertThat(processor.process(db, "u1", sms).transaction!!.categoryId).isEqualTo(CategoryIds.FUN)
    }

    @Test
    fun resortingLinksOldPaymentsAndFilesWhatItCan() = runTest {
        // Captured before categorising existed: no merchant, waiting in Other.
        fun old(id: String, payee: String) = TransactionEntity(
            id = id, amountPaise = 5000, direction = Direction.DEBIT, type = TransactionType.EXPENSE, timestamp = 1L,
            payeeRaw = payee, categoryId = CategoryIds.OTHER, confidence = 0f, status = TransactionStatus.NEEDS_REVIEW,
            sources = setOf(CaptureSource.SMS), createdAt = 1L, updatedAt = 1L, ownerUid = "u1",
        )
        db.transactionDao().insert(old("o1", "CRUST AND CO CAFE"))
        db.transactionDao().insert(old("o2", "RAMU BHAIYA"))
        db.transactionDao().insert(old("o3", "RAMU BHAIYA"))

        val filed = Recategorizer(clock).run(db, "u1")

        assertThat(filed).isEqualTo(1)
        assertThat(db.transactionDao().get("o1")!!.categoryId).isEqualTo(CategoryIds.FOOD_CANTEEN)
        val ramu = db.transactionDao().get("o2")!!
        assertThat(ramu.status).isEqualTo(TransactionStatus.NEEDS_REVIEW)
        assertThat(ramu.merchantId).isNotNull()
        assertThat(db.transactionDao().get("o3")!!.merchantId).isEqualTo(ramu.merchantId)
    }
}
