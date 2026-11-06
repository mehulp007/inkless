package com.mehul.inkless.capture

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.mehul.inkless.data.db.InklessDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class CaptureProcessorTest {

    private lateinit var db: InklessDatabase
    private val processor = CaptureProcessor(Clock.fixed(Instant.parse("2026-11-05T10:00:00Z"), ZoneOffset.UTC))

    private val payment = IncomingSms(
        sender = "VM-KOTAKB",
        body = "Sent Rs.249.00 from Kotak Bank AC X1234 to swiggy@icici on 05-11-26.UPI Ref 512345678904. Not you, https://kotak.com/KBANKT/Fraud",
        sentAt = 1_000L,
        receivedAt = 2_000L,
    )

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), InklessDatabase::class.java).build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun savesAPaymentWithItsMerchant() = runTest {
        assertThat(processor.process(db, "u1", payment)).isEqualTo(CaptureOutcome.SAVED)

        val merchant = db.merchantDao().findByVpa("swiggy@icici")
        assertThat(merchant).isNotNull()
        assertThat(merchant!!.timesSeen).isEqualTo(1)

        db.transactionDao().observeBetween(0, Long.MAX_VALUE).test {
            val rows = awaitItem()
            assertThat(rows).hasSize(1)
            assertThat(rows[0].amountPaise).isEqualTo(24_900)
            assertThat(rows[0].merchantName).isEqualTo("swiggy@icici")
        }
    }

    @Test
    fun theSameSmsTwiceIsSavedOnce() = runTest {
        processor.process(db, "u1", payment)
        val again = processor.process(db, "u1", payment.copy(receivedAt = 9_000L))

        assertThat(again).isEqualTo(CaptureOutcome.DUPLICATE)
        assertThat(db.transactionDao().count()).isEqualTo(1)
    }

    @Test
    fun aSecondPaymentToTheSameVpaReusesTheMerchant() = runTest {
        processor.process(db, "u1", payment)
        processor.process(db, "u1", payment.copy(body = payment.body.replace("512345678904", "512345678999"), sentAt = 5_000L))

        assertThat(db.transactionDao().count()).isEqualTo(2)
        assertThat(db.merchantDao().findByVpa("swiggy@icici")!!.timesSeen).isEqualTo(2)
    }

    @Test
    fun otpsLeaveNoTraceAtAll() = runTest {
        val otp = IncomingSms("VM-HDFCBK", "482193 is the OTP for your transaction of Rs.1499.00. Do not share.", 1L, 2L)

        assertThat(processor.process(db, "u1", otp)).isEqualTo(CaptureOutcome.SKIPPED)
        assertThat(db.transactionDao().count()).isEqualTo(0)
        db.rawCaptureDao().observeUnparsedCount().test { assertThat(awaitItem()).isEqualTo(0) }
        assertThat(db.rawCaptureDao().latestSmsTime()).isNull()
    }

    @Test
    fun unreadableBankAlertsAreKeptForTheCouldntReadList() = runTest {
        val odd = IncomingSms("VM-HDFCBK", "Your UPI-Lite balance was topped up. Transaction credited to wallet.", 1L, 2L)

        assertThat(processor.process(db, "u1", odd)).isEqualTo(CaptureOutcome.UNPARSED)
        assertThat(db.transactionDao().count()).isEqualTo(0)
        db.rawCaptureDao().observeUnparsed().test {
            val list = awaitItem()
            assertThat(list).hasSize(1)
            assertThat(list[0].body).isEqualTo(odd.body)
        }
    }
}
