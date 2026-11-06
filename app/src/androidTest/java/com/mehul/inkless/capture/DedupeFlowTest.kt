package com.mehul.inkless.capture

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mehul.inkless.data.db.InklessDatabase
import com.mehul.inkless.data.db.entity.AccountMatch
import com.mehul.inkless.data.db.entity.AccountRole
import com.mehul.inkless.data.db.entity.CaptureSource
import com.mehul.inkless.data.db.entity.MyAccountEntity
import com.mehul.inkless.data.db.entity.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** One payment = one transaction, whichever of the SMS and the app notification arrives first. */
@RunWith(AndroidJUnit4::class)
class DedupeFlowTest {

    private lateinit var db: InklessDatabase
    private val processor = CaptureProcessor(Clock.fixed(Instant.parse("2026-11-06T10:00:00Z"), ZoneOffset.UTC), TransactionClassifier())
    private val minute = 60_000L

    private fun sms(at: Long, ref: String = "512300000501") = IncomingSms(
        sender = "VM-KOTAKB-S",
        body = "Sent Rs.45.00 from Kotak Bank AC X4321 to q829154@ybl on 06-11-26.UPI Ref $ref. Not you, https://kotak.com/KBANKT/Fraud",
        sentAt = at,
        receivedAt = at,
    )

    private fun gpay(at: Long, key: String = "gpay-1") = IncomingNotification(
        packageName = "com.google.android.apps.nbu.paisa.user",
        key = key,
        text = "₹45.00 paid to Tapri near Block C\nTap to view details",
        postedAt = at,
    )

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), InklessDatabase::class.java).build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun smsThenNotificationIsOnePayment() = runTest {
        processor.process(db, "u1", sms(10 * minute))
        val second = processor.process(db, "u1", gpay(10 * minute + 20_000))

        assertThat(second.outcome).isEqualTo(CaptureOutcome.MERGED)
        assertThat(db.transactionDao().count()).isEqualTo(1)
        val merged = second.transaction!!
        assertThat(merged.sources).containsExactly(CaptureSource.SMS, CaptureSource.NOTIFICATION)
        assertThat(merged.upiRef).isEqualTo("512300000501")
        assertThat(merged.payeeRaw).isEqualTo("Tapri near Block C")
    }

    @Test
    fun notificationThenSmsIsOnePayment() = runTest {
        processor.process(db, "u1", gpay(10 * minute))
        val second = processor.process(db, "u1", sms(11 * minute))

        assertThat(second.outcome).isEqualTo(CaptureOutcome.MERGED)
        assertThat(db.transactionDao().count()).isEqualTo(1)
        assertThat(second.transaction!!.upiRef).isEqualTo("512300000501")
    }

    @Test
    fun aRepostedNotificationIsIgnored() = runTest {
        processor.process(db, "u1", gpay(10 * minute))
        assertThat(processor.process(db, "u1", gpay(10 * minute + 5_000)).outcome).isEqualTo(CaptureOutcome.DUPLICATE)
    }

    @Test
    fun twoSeparatePaymentsStayTwo() = runTest {
        processor.process(db, "u1", sms(10 * minute, ref = "512300000501"))
        processor.process(db, "u1", sms(11 * minute, ref = "512300000502"))
        processor.process(db, "u1", gpay(20 * minute, key = "gpay-2"))
        assertThat(db.transactionDao().count()).isEqualTo(3)
    }

    @Test
    fun transferToMyOwnAccountIsNotSpending() = runTest {
        db.myAccountDao().insert(MyAccountEntity("a1", AccountMatch.NAME, "MEHUL PATIL", AccountRole.MINE, null, "u1", 0))
        val sbi = IncomingSms(
            "VM-SBIUPI-S",
            "Dear UPI user A/C X8813 debited by 2000.00 on date 06Nov26 trf to MEHUL PATIL Refno 497500000601 If not u? call-1800111109-SBI",
            10 * minute,
            10 * minute,
        )
        val txn = processor.process(db, "u1", sbi).transaction!!
        assertThat(txn.type).isEqualTo(TransactionType.SELF_TRANSFER)
        assertThat(txn.categoryId).isNull()
    }
}
