package com.mehul.inkless.capture

import androidx.room.withTransaction
import com.mehul.inkless.data.db.InklessDatabase
import com.mehul.inkless.data.db.entity.Direction
import com.mehul.inkless.data.db.entity.SyncState
import com.mehul.inkless.data.db.entity.TransactionStatus
import com.mehul.inkless.data.db.entity.TransactionType
import com.mehul.inkless.data.seed.CategoryIds
import java.time.Duration
import javax.inject.Inject

/**
 * After the user adds or removes one of their own or home accounts, past payments are looked at
 * again so an old transfer to their own account stops counting as spending (and the reverse).
 * Anything the user confirmed themselves is left alone.
 */
class AccountReclassifier @Inject constructor() {

    suspend fun run(db: InklessDatabase, now: Long) {
        val accounts = db.myAccountDao().getAll()
        val dao = db.transactionDao()
        db.withTransaction {
            for (txn in dao.recentUnconfirmed(now - LOOK_BACK_MS)) {
                // Undo an earlier account-based decision first, so removing an account reverses it.
                val wasAccountBased = txn.type == TransactionType.SELF_TRANSFER || Classification.ALLOWANCE_TAG in txn.tags
                val base = if (!wasAccountBased) txn else {
                    val debit = txn.direction == Direction.DEBIT
                    txn.copy(
                        type = if (debit) TransactionType.EXPENSE else TransactionType.INCOME,
                        categoryId = if (debit) CategoryIds.OTHER else null,
                        status = if (debit) TransactionStatus.NEEDS_REVIEW else TransactionStatus.AUTO,
                        confidence = if (debit) 0f else 1f,
                        tags = txn.tags - Classification.ALLOWANCE_TAG,
                    )
                }
                val result = Classification.apply(base, accounts, earlierDebit = null)
                if (result != txn) dao.update(result.copy(updatedAt = now, syncState = SyncState.DIRTY))
            }
        }
    }

    private companion object {
        val LOOK_BACK_MS = Duration.ofDays(180).toMillis()
    }
}
