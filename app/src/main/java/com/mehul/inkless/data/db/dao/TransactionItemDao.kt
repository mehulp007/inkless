package com.mehul.inkless.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.mehul.inkless.data.db.entity.TransactionItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionItemDao {

    @Query("SELECT * FROM transaction_items WHERE transactionId = :transactionId ORDER BY sortOrder")
    fun observe(transactionId: String): Flow<List<TransactionItemEntity>>

    @Query("SELECT * FROM transaction_items WHERE transactionId = :transactionId ORDER BY sortOrder")
    suspend fun get(transactionId: String): List<TransactionItemEntity>

    @Query("SELECT * FROM transaction_items WHERE transactionId IN (:transactionIds) ORDER BY transactionId, sortOrder")
    suspend fun forTransactions(transactionIds: List<String>): List<TransactionItemEntity>

    /** Items of payments in a time range, for the per-category day totals on Home. */
    @Query(
        """
        SELECT i.* FROM transaction_items i
        JOIN transactions t ON t.id = i.transactionId
        WHERE t.deleted = 0 AND t.timestamp >= :startMillis AND t.timestamp < :endMillis
        ORDER BY i.transactionId, i.sortOrder
        """,
    )
    fun observeBetween(startMillis: Long, endMillis: Long): Flow<List<TransactionItemEntity>>

    @Insert
    suspend fun insertAll(items: List<TransactionItemEntity>)

    @Query("DELETE FROM transaction_items WHERE transactionId = :transactionId")
    suspend fun deleteFor(transactionId: String)

    /** Swaps a payment's whole breakdown for [items] (none removes it). */
    @Transaction
    suspend fun replace(transactionId: String, items: List<TransactionItemEntity>) {
        deleteFor(transactionId)
        if (items.isNotEmpty()) insertAll(items)
    }
}
