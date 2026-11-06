package com.mehul.inkless.ui.format

import com.mehul.inkless.data.db.entity.TransactionType

object Titles {
    /** What to call an entry when the bank's SMS named nobody (SBI refunds, for one). */
    fun fallback(type: TransactionType): String = when (type) {
        TransactionType.REFUND -> "Refund"
        TransactionType.INCOME -> "Money in"
        else -> "Payment"
    }
}
