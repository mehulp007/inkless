package com.mehul.inkless.breakdown

import com.google.common.truth.Truth.assertThat
import com.mehul.inkless.data.db.entity.CaptureSource
import com.mehul.inkless.data.db.entity.Direction
import com.mehul.inkless.data.db.entity.TransactionEntity
import com.mehul.inkless.data.db.entity.TransactionStatus
import com.mehul.inkless.data.db.entity.TransactionType
import org.junit.Test

class AfterPaymentTest {

    private val limit = 10_000L // ₹100

    private fun payment(
        paise: Long,
        status: TransactionStatus,
        type: TransactionType = TransactionType.EXPENSE,
        direction: Direction = Direction.DEBIT,
    ) = TransactionEntity(
        id = "t", amountPaise = paise, direction = direction, type = type, timestamp = 1, status = status,
        sources = setOf(CaptureSource.SMS), createdAt = 1, updatedAt = 1, ownerUid = "u1",
    )

    @Test
    fun `an unknown merchant is always asked about, with Break down only over the limit`() {
        assertThat(AfterPayment.of(payment(4_500, TransactionStatus.NEEDS_REVIEW), limit)).isEqualTo(AfterPayment.WHAT_WAS_THIS)
        assertThat(AfterPayment.of(payment(10_000, TransactionStatus.NEEDS_REVIEW), limit)).isEqualTo(AfterPayment.WHAT_WAS_THIS)
        assertThat(AfterPayment.of(payment(10_001, TransactionStatus.NEEDS_REVIEW), limit))
            .isEqualTo(AfterPayment.WHAT_WAS_THIS_OR_BREAK_DOWN)
    }

    @Test
    fun `a payment that filed itself gets a quiet check only over the limit`() {
        assertThat(AfterPayment.of(payment(9_000, TransactionStatus.AUTO), limit)).isEqualTo(AfterPayment.NOTHING)
        assertThat(AfterPayment.of(payment(45_000, TransactionStatus.AUTO), limit)).isEqualTo(AfterPayment.CHECK_IN)
    }

    @Test
    fun `switched off, nothing over the limit is asked`() {
        assertThat(AfterPayment.of(payment(45_000, TransactionStatus.AUTO), null)).isEqualTo(AfterPayment.NOTHING)
        assertThat(AfterPayment.of(payment(45_000, TransactionStatus.NEEDS_REVIEW), null)).isEqualTo(AfterPayment.WHAT_WAS_THIS)
    }

    @Test
    fun `money in, transfers and confirmed payments stay quiet`() {
        assertThat(AfterPayment.of(payment(500_000, TransactionStatus.AUTO, TransactionType.INCOME, Direction.CREDIT), limit))
            .isEqualTo(AfterPayment.NOTHING)
        assertThat(AfterPayment.of(payment(500_000, TransactionStatus.AUTO, TransactionType.SELF_TRANSFER), limit))
            .isEqualTo(AfterPayment.NOTHING)
        assertThat(AfterPayment.of(payment(500_000, TransactionStatus.CONFIRMED), limit)).isEqualTo(AfterPayment.NOTHING)
    }
}
