package com.mehul.inkless.capture

import com.google.common.truth.Truth.assertThat
import com.mehul.inkless.data.db.entity.CaptureSource
import com.mehul.inkless.data.db.entity.Direction
import com.mehul.inkless.data.db.entity.TransactionStatus
import com.mehul.inkless.data.db.entity.TransactionType
import com.mehul.inkless.data.seed.CategoryIds
import com.mehul.inkless.parsing.ParsedTxn
import com.mehul.inkless.parsing.TxnDirection
import org.junit.Test

class CaptureMapperTest {

    private val sms = IncomingSms(sender = "VM-HDFCBK", body = "Sent Rs.60.00 …", sentAt = 1_000L, receivedAt = 2_000L)

    private fun map(parsed: ParsedTxn) =
        CaptureMapper.toTransaction("t1", parsed, sms, merchantId = "m1", ownerUid = "u1", now = 5_000L)

    @Test
    fun `payments go to Other and wait for review`() {
        val txn = map(ParsedTxn(6000, TxnDirection.DEBIT, vpa = "chai@ybl", upiRef = "512345678905"))
        assertThat(txn.type).isEqualTo(TransactionType.EXPENSE)
        assertThat(txn.direction).isEqualTo(Direction.DEBIT)
        assertThat(txn.categoryId).isEqualTo(CategoryIds.OTHER)
        assertThat(txn.status).isEqualTo(TransactionStatus.NEEDS_REVIEW)
        assertThat(txn.sources).containsExactly(CaptureSource.SMS)
        assertThat(txn.upiRef).isEqualTo("512345678905")
        assertThat(txn.merchantId).isEqualTo("m1")
    }

    @Test
    fun `money in is income with no category`() {
        val txn = map(ParsedTxn(1_200_000, TxnDirection.CREDIT))
        assertThat(txn.type).isEqualTo(TransactionType.INCOME)
        assertThat(txn.categoryId).isNull()
        assertThat(txn.status).isEqualTo(TransactionStatus.AUTO)
    }

    @Test
    fun `refunds wait for a category so they can offset it`() {
        val txn = map(ParsedTxn(79_900, TxnDirection.CREDIT, isRefund = true))
        assertThat(txn.type).isEqualTo(TransactionType.REFUND)
        assertThat(txn.status).isEqualTo(TransactionStatus.NEEDS_REVIEW)
    }

    @Test
    fun `uses the bank's sent time, falling back to when the phone got it`() {
        assertThat(map(ParsedTxn(100, TxnDirection.DEBIT)).timestamp).isEqualTo(1_000L)
        val noSentTime = CaptureMapper.toTransaction(
            "t2", ParsedTxn(100, TxnDirection.DEBIT), sms.copy(sentAt = 0), null, "u1", 5_000L,
        )
        assertThat(noSentTime.timestamp).isEqualTo(2_000L)
    }

    @Test
    fun `the same sms hashes the same from the receiver and the inbox`() {
        val fromInbox = sms.copy(receivedAt = 2_500L)
        assertThat(CaptureMapper.hash(fromInbox)).isEqualTo(CaptureMapper.hash(sms))
        assertThat(CaptureMapper.hash(sms.copy(sentAt = 1_001L))).isNotEqualTo(CaptureMapper.hash(sms))
    }
}
