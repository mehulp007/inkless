package com.mehul.inkless.capture

import com.mehul.inkless.categorize.Categorizer
import com.mehul.inkless.categorize.Decision
import com.mehul.inkless.data.db.entity.CaptureSource
import com.mehul.inkless.data.db.entity.Direction
import com.mehul.inkless.data.db.entity.TransactionEntity
import com.mehul.inkless.data.db.entity.TransactionStatus
import com.mehul.inkless.data.db.entity.TransactionType
import com.mehul.inkless.data.seed.CategoryIds
import com.mehul.inkless.parsing.ParsedTxn
import com.mehul.inkless.parsing.TxnDirection
import java.security.MessageDigest

/** An SMS as Android hands it to us, from either the live receiver or the inbox. */
data class IncomingSms(
    val sender: String,
    val body: String,
    /** When the bank's SMS centre sent it; the same value in the live broadcast and in the inbox. */
    val sentAt: Long,
    val receivedAt: Long,
)

object CaptureMapper {

    /**
     * Identifies one SMS, so the live receiver and a later inbox import don't both save it.
     * The sent time is part of it because two chai payments can produce word-for-word identical alerts.
     */
    fun hash(sms: IncomingSms): String {
        val key = "${sms.sender.trim().uppercase()}\n${sms.body}\n${sms.sentAt.takeIf { it > 0 } ?: sms.receivedAt}"
        return MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    /**
     * Incoming money isn't spending, so it gets no category. Payments and refunds take the
     * categoriser's [decision]; with no confident decision they land in Other and wait for review.
     */
    fun toTransaction(
        id: String,
        parsed: ParsedTxn,
        sms: IncomingSms,
        merchantId: String?,
        ownerUid: String,
        now: Long,
        decision: Decision? = null,
    ): TransactionEntity {
        val isDebit = parsed.direction == TxnDirection.DEBIT
        val type = when {
            isDebit -> TransactionType.EXPENSE
            parsed.isRefund -> TransactionType.REFUND
            else -> TransactionType.INCOME
        }
        val needsCategory = type != TransactionType.INCOME
        val filed = decision?.takeIf { it.confidence >= Categorizer.AUTO_FILE_THRESHOLD }
        return TransactionEntity(
            id = id,
            amountPaise = parsed.amountPaise,
            direction = if (isDebit) Direction.DEBIT else Direction.CREDIT,
            type = type,
            timestamp = sms.sentAt.takeIf { it > 0 } ?: sms.receivedAt,
            vpa = parsed.vpa,
            payeeRaw = parsed.payee,
            merchantId = merchantId,
            accountLast4 = parsed.accountLast4,
            bankCode = parsed.bankCode,
            upiRef = parsed.upiRef,
            balanceAfterPaise = parsed.balancePaise,
            categoryId = if (needsCategory) filed?.categoryId ?: CategoryIds.OTHER else null,
            confidence = if (needsCategory) filed?.confidence ?: 0f else 1f,
            status = if (needsCategory && filed == null) TransactionStatus.NEEDS_REVIEW else TransactionStatus.AUTO,
            sources = setOf(CaptureSource.SMS),
            createdAt = now,
            updatedAt = now,
            ownerUid = ownerUid,
        )
    }
}
