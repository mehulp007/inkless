package com.mehul.inkless.breakdown

import com.mehul.inkless.data.db.entity.Direction
import com.mehul.inkless.data.db.entity.TransactionEntity
import com.mehul.inkless.data.db.entity.TransactionStatus
import com.mehul.inkless.data.db.entity.TransactionType

/** The one notification (if any) a freshly captured payment gets. */
enum class AfterPayment {
    /** Filed on its own and small, or not spending at all: stay quiet. */
    NOTHING,

    /** Unknown merchant: "What was this?" with category buttons. */
    WHAT_WAS_THIS,

    /** Unknown merchant and over the limit: the same question, with a Break down button. */
    WHAT_WAS_THIS_OR_BREAK_DOWN,

    /** Filed on its own but over the limit: "₹450 → Food Delivery" with Looks right / Break down / Change. */
    CHECK_IN,
    ;

    companion object {
        /** [askAbovePaise] is null when the user switched the over-the-limit questions off. */
        fun of(txn: TransactionEntity, askAbovePaise: Long?): AfterPayment {
            if (txn.type != TransactionType.EXPENSE || txn.direction != Direction.DEBIT) return NOTHING
            val big = askAbovePaise != null && txn.amountPaise > askAbovePaise
            return when {
                txn.status == TransactionStatus.NEEDS_REVIEW -> if (big) WHAT_WAS_THIS_OR_BREAK_DOWN else WHAT_WAS_THIS
                big && txn.status == TransactionStatus.AUTO -> CHECK_IN
                else -> NOTHING
            }
        }
    }
}
