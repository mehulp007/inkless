package com.mehul.inkless.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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
)

data class CategoryTotal(
    val categoryId: String,
    val totalPaise: Long,
    val count: Int,
)

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<TransactionEntity>)

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int

    @Query("SELECT MAX(timestamp) FROM transactions")
    suspend fun latestTimestamp(): Long?

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    @Query(
        """
        SELECT t.id, t.amountPaise, t.direction, t.type, t.timestamp, t.categoryId,
               t.vpa, t.payeeRaw, m.displayName AS merchantName, t.status
        FROM transactions t
        LEFT JOIN merchants m ON m.id = t.merchantId
        WHERE t.deleted = 0 AND t.timestamp >= :startMillis AND t.timestamp < :endMillis
        ORDER BY t.timestamp DESC
        """,
    )
    fun observeBetween(startMillis: Long, endMillis: Long): Flow<List<TransactionListItem>>

    /** Spend per category in a time range. Refunds count against the category they came back to. */
    @Query(
        """
        SELECT categoryId,
               SUM(CASE WHEN direction = 'DEBIT' THEN amountPaise ELSE -amountPaise END) AS totalPaise,
               COUNT(*) AS count
        FROM transactions
        WHERE deleted = 0
          AND categoryId IS NOT NULL
          AND type IN ('EXPENSE', 'REFUND')
          AND timestamp >= :startMillis AND timestamp < :endMillis
        GROUP BY categoryId
        """,
    )
    fun observeCategoryTotals(startMillis: Long, endMillis: Long): Flow<List<CategoryTotal>>
}
