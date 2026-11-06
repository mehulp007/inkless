package com.mehul.inkless.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mehul.inkless.data.db.entity.Direction
import com.mehul.inkless.data.db.entity.TransactionEntity
import com.mehul.inkless.data.db.entity.TransactionStatus
import com.mehul.inkless.data.db.entity.TransactionType
import kotlinx.coroutines.flow.Flow

data class TransactionListItem(
    val id: String,
    val amountPaise: Long,
    val direction: Direction,
    val type: TransactionType,
    val timestamp: Long,
    val categoryId: String?,
    val vpa: String?,
    val payeeRaw: String?,
    val merchantName: String?,
    val status: TransactionStatus,
    /** How many items the payment is broken down into; 0 when it isn't. */
    val itemCount: Int = 0,
)

/** A payment still waiting for a category, with what's needed to try again. */
data class ReviewCandidate(
    val id: String,
    val vpa: String?,
    val payeeRaw: String?,
    val merchantCategoryId: String?,
)

/** Captured before merchants were keyed by name too; linked up on the next re-sort. */
data class UnlinkedPayment(val id: String, val vpa: String?, val payeeRaw: String?)

data class CategoryTotal(
    val categoryId: String,
    val totalPaise: Long,
    val count: Int,
)

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<TransactionEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(transaction: TransactionEntity)

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun observe(id: String): Flow<TransactionEntity?>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun get(id: String): TransactionEntity?

    @Query(
        """
        SELECT t.id, t.vpa, t.payeeRaw, m.defaultCategoryId AS merchantCategoryId
        FROM transactions t
        LEFT JOIN merchants m ON m.id = t.merchantId
        WHERE t.status = 'NEEDS_REVIEW' AND t.deleted = 0
        """,
    )
    suspend fun needsReview(): List<ReviewCandidate>

    @Query(
        """
        UPDATE transactions SET categoryId = :categoryId, status = :status, confidence = :confidence,
            updatedAt = :now, syncState = 'DIRTY'
        WHERE id = :id
        """,
    )
    suspend fun file(id: String, categoryId: String, status: TransactionStatus, confidence: Float, now: Long)

    /** Files every waiting payment to this merchant once the user has said what it is. */
    @Query(
        """
        UPDATE transactions SET categoryId = :categoryId, status = 'AUTO', confidence = 1.0,
            updatedAt = :now, syncState = 'DIRTY'
        WHERE merchantId = :merchantId AND status = 'NEEDS_REVIEW' AND deleted = 0
        """,
    )
    suspend fun fileWaitingForMerchant(merchantId: String, categoryId: String, now: Long): Int

    @Query(
        """
        SELECT t.id, t.amountPaise, t.direction, t.type, t.timestamp, t.categoryId,
               t.vpa, t.payeeRaw, m.displayName AS merchantName, t.status,
               (SELECT COUNT(*) FROM transaction_items i WHERE i.transactionId = t.id) AS itemCount
        FROM transactions t
        LEFT JOIN merchants m ON m.id = t.merchantId
        WHERE t.deleted = 0 AND t.status = 'NEEDS_REVIEW'
        ORDER BY t.timestamp DESC
        """,
    )
    fun observeNeedsReview(): Flow<List<TransactionListItem>>

    /** Categories the user files things under most, for the quick-pick buttons. */
    @Query(
        """
        SELECT categoryId FROM transactions
        WHERE deleted = 0 AND categoryId IS NOT NULL AND categoryId != 'other'
          AND type = 'EXPENSE' AND status != 'NEEDS_REVIEW' AND timestamp >= :since
        GROUP BY categoryId ORDER BY COUNT(*) DESC LIMIT :limit
        """,
    )
    suspend fun mostUsedCategories(since: Long, limit: Int): List<String>

    @Query(
        """
        SELECT id, vpa, payeeRaw FROM transactions
        WHERE merchantId IS NULL AND deleted = 0 AND type != 'INCOME' AND (vpa IS NOT NULL OR payeeRaw IS NOT NULL)
          AND sources NOT LIKE '%MANUAL%'
        """,
    )
    suspend fun unlinked(): List<UnlinkedPayment>

    @Query("UPDATE transactions SET merchantId = :merchantId, updatedAt = :now, syncState = 'DIRTY' WHERE id = :id")
    suspend fun linkMerchant(id: String, merchantId: String, now: Long)

    @Query("SELECT * FROM transactions WHERE deleted = 0 AND status != 'CONFIRMED' AND timestamp >= :since")
    suspend fun recentUnconfirmed(since: Long): List<TransactionEntity>

    /** The latest payment to a merchant in a time range, for spotting refunds. */
    @Query(
        """
        SELECT * FROM transactions
        WHERE merchantId = :merchantId AND direction = 'DEBIT' AND type = 'EXPENSE' AND deleted = 0
          AND timestamp >= :from AND timestamp <= :to
        ORDER BY timestamp DESC LIMIT 1
        """,
    )
    suspend fun lastDebitTo(merchantId: String, from: Long, to: Long): TransactionEntity?

    /** Candidates for "is this the same payment?" around a new capture. */
    @Query("SELECT * FROM transactions WHERE deleted = 0 AND timestamp BETWEEN :from AND :to")
    suspend fun around(from: Long, to: Long): List<TransactionEntity>

    /** Spending since [since], newest first: what a shared order can be matched to. */
    @Query(
        """
        SELECT * FROM transactions
        WHERE deleted = 0 AND type = 'EXPENSE' AND direction = 'DEBIT' AND timestamp >= :since
        ORDER BY timestamp DESC LIMIT :limit
        """,
    )
    suspend fun recentSpending(since: Long, limit: Int): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE deleted = 0 AND upiRef = :ref")
    suspend fun byRef(ref: String): List<TransactionEntity>

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int

    @Query("SELECT MAX(timestamp) FROM transactions")
    suspend fun latestTimestamp(): Long?

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    @Query(
        """
        SELECT t.id, t.amountPaise, t.direction, t.type, t.timestamp, t.categoryId,
               t.vpa, t.payeeRaw, m.displayName AS merchantName, t.status,
               (SELECT COUNT(*) FROM transaction_items i WHERE i.transactionId = t.id) AS itemCount
        FROM transactions t
        LEFT JOIN merchants m ON m.id = t.merchantId
        WHERE t.deleted = 0 AND t.timestamp >= :startMillis AND t.timestamp < :endMillis
        ORDER BY t.timestamp DESC
        """,
    )
    fun observeBetween(startMillis: Long, endMillis: Long): Flow<List<TransactionListItem>>

    /**
     * Spend per category in a time range, from the `spend_parts` view: refunds count against the
     * category they came back to, and a broken-down payment counts each item in its own category.
     * This is the only place category totals are worked out; anything showing spend per category
     * reads it (through LedgerRepository.categoryTotalsBetween) rather than adding up payments itself.
     */
    @Query(
        """
        SELECT categoryId, SUM(amountPaise) AS totalPaise, COUNT(DISTINCT transactionId) AS count
        FROM spend_parts
        WHERE categoryId IS NOT NULL AND timestamp >= :startMillis AND timestamp < :endMillis
        GROUP BY categoryId
        """,
    )
    fun observeCategoryTotals(startMillis: Long, endMillis: Long): Flow<List<CategoryTotal>>
}
