package com.mehul.inkless.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.mehul.inkless.data.db.SpendPart
import kotlinx.coroutines.flow.Flow

data class MerchantSpend(
    val merchantId: String?,
    val merchantName: String?,
    val totalPaise: Long,
    val count: Int,
)

/** Spending as counted for categories and budgets, read from the `spend_parts` view. */
@Dao
interface SpendDao {

    @Query("SELECT * FROM spend_parts WHERE timestamp >= :startMillis AND timestamp < :endMillis ORDER BY timestamp")
    fun observeBetween(startMillis: Long, endMillis: Long): Flow<List<SpendPart>>

    @Query("SELECT * FROM spend_parts WHERE timestamp >= :startMillis AND timestamp < :endMillis ORDER BY timestamp")
    suspend fun between(startMillis: Long, endMillis: Long): List<SpendPart>

    /** Where a category's money went, biggest first. A cash entry has no merchant. */
    @Query(
        """
        SELECT p.merchantId, m.displayName AS merchantName, SUM(p.amountPaise) AS totalPaise,
               COUNT(DISTINCT p.transactionId) AS count
        FROM spend_parts p
        LEFT JOIN merchants m ON m.id = p.merchantId
        WHERE p.categoryId = :categoryId AND p.timestamp >= :startMillis AND p.timestamp < :endMillis
        GROUP BY p.merchantId
        HAVING SUM(p.amountPaise) > 0
        ORDER BY totalPaise DESC
        LIMIT :limit
        """,
    )
    fun observeTopMerchants(categoryId: String, startMillis: Long, endMillis: Long, limit: Int): Flow<List<MerchantSpend>>
}
