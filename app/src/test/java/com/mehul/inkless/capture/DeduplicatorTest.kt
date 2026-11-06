package com.mehul.inkless.capture

import com.google.common.truth.Truth.assertThat
import com.mehul.inkless.data.db.entity.CaptureSource
import com.mehul.inkless.data.db.entity.Direction
import com.mehul.inkless.data.db.entity.TransactionEntity
import com.mehul.inkless.data.db.entity.TransactionStatus
import com.mehul.inkless.data.db.entity.TransactionType
import com.mehul.inkless.data.seed.CategoryIds
import org.junit.Test

class DeduplicatorTest {

    private val minute = 60_000L

    private fun txn(
        id: String,
        source: CaptureSource,
        amount: Long = 4500,
        at: Long = 10 * minute,
        direction: Direction = Direction.DEBIT,
        ref: String? = null,
        vpa: String? = null,
        payee: String? = null,
        category: String? = CategoryIds.OTHER,
        status: TransactionStatus = TransactionStatus.NEEDS_REVIEW,
        confidence: Float = 0f,
        account: String? = null,
    ) = TransactionEntity(
        id = id, amountPaise = amount, direction = direction, type = TransactionType.EXPENSE, timestamp = at,
        vpa = vpa, payeeRaw = payee, upiRef = ref, accountLast4 = account, categoryId = category,
        confidence = confidence, status = status, sources = setOf(source), createdAt = at, updatedAt = at, ownerUid = "u1",
    )

    @Test
    fun `same reference is the same payment, a different one never is`() {
        val sms = txn("s", CaptureSource.SMS, ref = "512300000001")
        assertThat(Deduplicator.isSamePayment(sms, txn("n", CaptureSource.NOTIFICATION, ref = "512300000001", at = 30 * minute))).isTrue()
        assertThat(Deduplicator.isSamePayment(sms, txn("n", CaptureSource.NOTIFICATION, ref = "512300000099"))).isFalse()
    }

    @Test
    fun `sms and notification within three minutes with the same amount match`() {
        val sms = txn("s", CaptureSource.SMS, vpa = "q829154@ybl")
        val notification = txn("n", CaptureSource.NOTIFICATION, at = 12 * minute, payee = "Tapri near Block C")
        assertThat(Deduplicator.isSamePayment(sms, notification)).isTrue()
    }

    @Test
    fun `too far apart, different amount or opposite direction never match`() {
        val sms = txn("s", CaptureSource.SMS)
        assertThat(Deduplicator.isSamePayment(sms, txn("n", CaptureSource.NOTIFICATION, at = 14 * minute))).isFalse()
        assertThat(Deduplicator.isSamePayment(sms, txn("n", CaptureSource.NOTIFICATION, amount = 4600))).isFalse()
        assertThat(Deduplicator.isSamePayment(sms, txn("n", CaptureSource.NOTIFICATION, direction = Direction.CREDIT))).isFalse()
    }

    @Test
    fun `two sms for the same amount are two payments`() {
        // Two ₹45 chais a minute apart.
        assertThat(Deduplicator.isSamePayment(txn("a", CaptureSource.SMS), txn("b", CaptureSource.SMS, at = 11 * minute))).isFalse()
    }

    @Test
    fun `payees must not contradict each other`() {
        val sms = txn("s", CaptureSource.SMS, payee = "TEMPTATION JAIPU")
        assertThat(Deduplicator.isSamePayment(sms, txn("n", CaptureSource.NOTIFICATION, payee = "Temptation Restaurant Jaipur"))).isTrue()
        assertThat(Deduplicator.isSamePayment(sms, txn("n", CaptureSource.NOTIFICATION, payee = "Sharma Canteen"))).isFalse()

        val masked = txn("s", CaptureSource.SMS, vpa = "*1265@upi")
        assertThat(Deduplicator.isSamePayment(masked, txn("n", CaptureSource.NOTIFICATION, vpa = "rohan1265@upi"))).isTrue()
        assertThat(Deduplicator.isSamePayment(masked, txn("n", CaptureSource.NOTIFICATION, vpa = "priya@okaxis"))).isFalse()
    }

    @Test
    fun `findMatch prefers the reference, then the closest in time`() {
        val incoming = txn("n", CaptureSource.NOTIFICATION, at = 10 * minute)
        val far = txn("far", CaptureSource.SMS, at = 12 * minute)
        val near = txn("near", CaptureSource.SMS, at = 10 * minute + 5_000)
        assertThat(Deduplicator.findMatch(incoming, listOf(far, near))!!.id).isEqualTo("near")

        val withRef = incoming.copy(upiRef = "512300000001")
        val byRef = txn("ref", CaptureSource.SMS, at = 40 * minute, ref = "512300000001")
        assertThat(Deduplicator.findMatch(withRef, listOf(near, byRef))!!.id).isEqualTo("ref")
    }

    @Test
    fun `merge trusts the sms for money and the notification for the name`() {
        val sms = txn("s", CaptureSource.SMS, amount = 4500, ref = "512300000001", vpa = "q829154@ybl", payee = null, account = "8813")
        val notification = txn("n", CaptureSource.NOTIFICATION, amount = 4500, at = 11 * minute, payee = "Tapri near Block C")

        val merged = Deduplicator.merge(existing = sms, incoming = notification, now = 99L)

        assertThat(merged.id).isEqualTo("s")
        assertThat(merged.upiRef).isEqualTo("512300000001")
        assertThat(merged.accountLast4).isEqualTo("8813")
        assertThat(merged.vpa).isEqualTo("q829154@ybl")
        assertThat(merged.payeeRaw).isEqualTo("Tapri near Block C")
        assertThat(merged.timestamp).isEqualTo(sms.timestamp)
        assertThat(merged.sources).containsExactly(CaptureSource.SMS, CaptureSource.NOTIFICATION)
    }

    @Test
    fun `merge keeps what the user confirmed, and takes a better guess over none`() {
        val confirmed = txn("s", CaptureSource.SMS, category = CategoryIds.FUN, status = TransactionStatus.CONFIRMED, confidence = 1f)
        val guessed = txn("n", CaptureSource.NOTIFICATION, category = CategoryIds.FOOD_DELIVERY, status = TransactionStatus.AUTO, confidence = 0.85f)
        assertThat(Deduplicator.merge(confirmed, guessed, 0).categoryId).isEqualTo(CategoryIds.FUN)

        val waiting = txn("s", CaptureSource.SMS)
        val merged = Deduplicator.merge(waiting, guessed, 0)
        assertThat(merged.categoryId).isEqualTo(CategoryIds.FOOD_DELIVERY)
        assertThat(merged.status).isEqualTo(TransactionStatus.AUTO)
    }
}
