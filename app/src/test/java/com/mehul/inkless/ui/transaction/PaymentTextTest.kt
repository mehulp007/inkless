package com.mehul.inkless.ui.transaction

import com.google.common.truth.Truth.assertThat
import com.mehul.inkless.data.db.dao.TransactionListItem
import com.mehul.inkless.data.db.entity.CaptureSource
import com.mehul.inkless.data.db.entity.Direction
import com.mehul.inkless.data.db.entity.TransactionEntity
import com.mehul.inkless.data.db.entity.TransactionStatus
import com.mehul.inkless.data.db.entity.TransactionType
import com.mehul.inkless.share.totalLine
import com.mehul.inkless.ui.review.sortMeta
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Test

class PaymentTextTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val at = LocalDate.of(2026, 11, 5).atTime(18, 26).atZone(zone).toInstant().toEpochMilli()

    private fun txn(
        type: TransactionType = TransactionType.EXPENSE,
        direction: Direction = Direction.DEBIT,
        status: TransactionStatus = TransactionStatus.CONFIRMED,
        sources: Set<CaptureSource> = setOf(CaptureSource.SMS),
        upiRef: String? = null,
    ) = TransactionEntity(
        id = "t", amountPaise = 42_000, direction = direction, type = type, timestamp = at,
        accountLast4 = "4821", bankCode = "SBI", upiRef = upiRef, status = status, sources = sources,
        createdAt = 0, updatedAt = 0, ownerUid = "u",
    )

    private val empty = TransactionScreenState(loading = false)

    @Test
    fun `what kind of entry it is`() {
        assertThat(typeLine(txn(), empty)).isEqualTo("Expense")
        assertThat(typeLine(txn(status = TransactionStatus.NEEDS_REVIEW), empty)).isEqualTo("Expense · to sort")
        assertThat(typeLine(txn(sources = setOf(CaptureSource.MANUAL)), empty)).isEqualTo("Cash expense")
        assertThat(typeLine(txn(type = TransactionType.REFUND, direction = Direction.CREDIT), empty)).isEqualTo("Refund · counts against its category")
        assertThat(typeLine(txn(type = TransactionType.INCOME, direction = Direction.CREDIT), empty)).isEqualTo("Money in · not spending")
        assertThat(typeLine(txn(type = TransactionType.SELF_TRANSFER), empty)).isEqualTo("Own-account transfer · not spending")
    }

    @Test
    fun `the bank's facts, with only the end of the reference`() {
        val facts = facts(txn(sources = setOf(CaptureSource.NOTIFICATION, CaptureSource.SMS), upiRef = "849625796058"), zone).toMap()
        assertThat(facts["Date"]).isEqualTo("5 Nov 2026 · 18:26")
        assertThat(facts["Source"]).isEqualTo("Bank SMS + UPI app")
        assertThat(facts["Paid from"]).isEqualTo("SBI •• 4821")
        assertThat(facts["UPI reference"]).isEqualTo("•••• 6058")
        assertThat(facts(txn(), zone).toMap()).doesNotContainKey("UPI reference")
    }

    @Test
    fun `items in a sentence`() {
        assertThat(itemsSentence(listOf("Milk", "Bananas", "Snacks", "A notebook")))
            .isEqualTo("Milk, bananas, snacks and a notebook. Category totals use each item's amount.")
        assertThat(itemsSentence(emptyList())).isEqualTo("Category totals use each item's amount.")
    }

    @Test
    fun `how a shared order compares with its payment`() {
        assertThat(totalLine(42_000, 42_000)).isEqualTo("Total · matches payment")
        assertThat(totalLine(40_000, 42_000)).isEqualTo("Total · ₹20 less than the payment")
        assertThat(totalLine(45_000, 42_000)).isEqualTo("Total · ₹30 more than the payment")
        assertThat(totalLine(45_000, null)).isEqualTo("Total")
    }

    @Test
    fun `a card on the To sort tab says when and how`() {
        val item = TransactionListItem(
            id = "q", amountPaise = 5_000, direction = Direction.DEBIT, type = TransactionType.EXPENSE, timestamp = at,
            categoryId = null, vpa = null, payeeRaw = null, merchantName = null, status = TransactionStatus.NEEDS_REVIEW,
            sources = setOf(CaptureSource.SMS, CaptureSource.NOTIFICATION),
        )
        assertThat(sortMeta(item, zone)).isEqualTo("5 Nov · 18:26 · Bank SMS + notification")
        assertThat(sortMeta(item.copy(sources = emptySet()), zone)).isEqualTo("5 Nov · 18:26")
    }
}
