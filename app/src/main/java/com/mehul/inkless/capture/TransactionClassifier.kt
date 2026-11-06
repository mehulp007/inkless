package com.mehul.inkless.capture

import com.mehul.inkless.breakdown.Breakdown
import com.mehul.inkless.breakdown.DraftItem
import com.mehul.inkless.data.db.InklessDatabase
import com.mehul.inkless.data.db.entity.AccountMatch
import com.mehul.inkless.data.db.entity.AccountRole
import com.mehul.inkless.data.db.entity.Direction
import com.mehul.inkless.data.db.entity.MyAccountEntity
import com.mehul.inkless.data.db.entity.TransactionEntity
import com.mehul.inkless.data.db.entity.TransactionStatus
import com.mehul.inkless.data.db.entity.TransactionType
import java.time.Duration
import javax.inject.Inject

/**
 * Works out what kind of money movement a capture is, beyond plain spending:
 * transfers between the user's own accounts, allowance from home, and refunds of earlier payments.
 */
class TransactionClassifier @Inject constructor() {

    suspend fun classify(db: InklessDatabase, draft: TransactionEntity): TransactionEntity {
        val accounts = db.myAccountDao().getAll()
        val earlier = if (draft.direction == Direction.CREDIT && draft.merchantId != null) {
            db.transactionDao().lastDebitTo(draft.merchantId, draft.timestamp - REFUND_WINDOW_MS, draft.timestamp)
        } else {
            null
        }
        val earlierItems = earlier?.let { payment ->
            db.transactionItemDao().get(payment.id).map { DraftItem(it.amountPaise, it.categoryId, it.note) }
        }.orEmpty()
        return Classification.apply(draft, accounts, earlier, earlierItems)
    }

    companion object {
        val REFUND_WINDOW_MS: Long = Duration.ofDays(30).toMillis()
    }
}

/** The decisions themselves, kept free of the database so they can be unit-tested. */
object Classification {

    const val ALLOWANCE_TAG = "allowance"

    private val word = Regex("[a-z0-9]+")

    /** [earlierItems] is [earlierDebit]'s breakdown, if it has one. */
    fun apply(
        draft: TransactionEntity,
        accounts: List<MyAccountEntity>,
        earlierDebit: TransactionEntity?,
        earlierItems: List<DraftItem> = emptyList(),
    ): TransactionEntity {
        // Something the user already decided is left alone.
        if (draft.status == TransactionStatus.CONFIRMED) return draft

        when (roleOf(draft, accounts)) {
            AccountRole.MINE -> return draft.copy(
                type = TransactionType.SELF_TRANSFER,
                categoryId = null,
                status = TransactionStatus.AUTO,
                confidence = 1f,
            )
            AccountRole.HOME -> if (draft.direction == Direction.CREDIT) {
                return draft.copy(
                    type = TransactionType.INCOME,
                    categoryId = null,
                    status = TransactionStatus.AUTO,
                    confidence = 1f,
                    tags = (draft.tags + ALLOWANCE_TAG).distinct(),
                )
            }
            null -> Unit
        }

        // Money back from a merchant paid at least as much in the last 30 days counts against
        // what that payment was for. For a broken-down payment that's the item of the same amount,
        // and if no item clearly matches, the user is asked.
        if (draft.direction == Direction.CREDIT && earlierDebit != null && draft.amountPaise <= earlierDebit.amountPaise) {
            val category = if (earlierItems.isEmpty()) {
                earlierDebit.categoryId
            } else {
                Breakdown.refundCategory(draft.amountPaise, earlierItems)
            }
            return draft.copy(
                type = TransactionType.REFUND,
                categoryId = category ?: draft.categoryId,
                status = if (category != null && earlierDebit.status != TransactionStatus.NEEDS_REVIEW) {
                    TransactionStatus.AUTO
                } else {
                    TransactionStatus.NEEDS_REVIEW
                },
                confidence = if (category != null) 0.9f else 0f,
            )
        }
        return draft
    }

    /** Whether the other side of this payment is one of the user's own or home accounts. */
    fun roleOf(txn: TransactionEntity, accounts: List<MyAccountEntity>): AccountRole? =
        accounts.firstOrNull { matches(txn, it) }?.role

    private fun matches(txn: TransactionEntity, account: MyAccountEntity): Boolean {
        val value = account.value.trim().lowercase()
        if (value.isEmpty()) return false
        return when (account.match) {
            AccountMatch.UPI_ID -> txn.vpa?.lowercase()?.let { vpa ->
                // Banks sometimes mask the start of a UPI ID ("*1265@upi").
                vpa == value || (vpa.startsWith('*') && value.endsWith(vpa.removePrefix("*")))
            } ?: false
            // All the words of the saved name appear in the payee, in any order.
            AccountMatch.NAME -> txn.payeeRaw?.lowercase()?.let { payee ->
                val payeeWords = word.findAll(payee).map { it.value }.toSet()
                word.findAll(value).map { it.value }.all { it in payeeWords }
            } ?: false
        }
    }
}
