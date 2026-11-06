package com.mehul.inkless.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.mehul.inkless.data.db.entity.ModelCountEntity
import com.mehul.inkless.data.db.entity.PredictionEntity
import com.mehul.inkless.data.db.entity.PredictionSource
import com.mehul.inkless.data.db.entity.RecurringEntity
import kotlinx.coroutines.flow.Flow

/** A payment the model can learn from. */
data class TrainingRow(
    val vpa: String?,
    val payeeRaw: String?,
    val amountPaise: Long,
    val timestamp: Long,
    val categoryId: String,
    val merchantId: String?,
)

/** A guess next to the category the payment has now. */
data class GuessOutcome(
    val createdAt: Long,
    val source: PredictionSource,
    val filedCategoryId: String?,
    val modelCategoryId: String?,
    val finalCategoryId: String?,
)

/** A spending payment with what it was paid to, for recurring detection and Ask Inkless. */
data class PaymentRow(
    val id: String,
    val timestamp: Long,
    val amountPaise: Long,
    val categoryId: String?,
    val merchantId: String?,
    val title: String?,
    val vpa: String?,
)

@Dao
interface InsightDao {

    /**
     * Payments with a category someone stands behind: picked or confirmed by the user, or filed by
     * their rules, merchant memory or the keyword list. Ones the model filed itself are left out
     * until the user confirms them, so it doesn't learn from its own guesses; so are broken-down
     * payments (one payee, many categories).
     */
    @Query(
        """
        SELECT t.vpa, t.payeeRaw, t.amountPaise, t.timestamp, t.categoryId, t.merchantId
        FROM transactions t
        LEFT JOIN predictions p ON p.transactionId = t.id
        WHERE t.deleted = 0 AND t.type = 'EXPENSE' AND t.direction = 'DEBIT' AND t.categoryId IS NOT NULL
          AND t.status != 'NEEDS_REVIEW'
          AND NOT (t.status = 'AUTO' AND COALESCE(p.source, '') = 'MODEL')
          AND NOT EXISTS (SELECT 1 FROM transaction_items i WHERE i.transactionId = t.id)
        ORDER BY t.timestamp
        """,
    )
    fun observeTrainingRows(): Flow<List<TrainingRow>>

    @Query("SELECT * FROM model_counts")
    suspend fun counts(): List<ModelCountEntity>

    @Query("DELETE FROM model_counts")
    suspend fun clearCounts()

    @Insert
    suspend fun insertCounts(rows: List<ModelCountEntity>)

    @Transaction
    suspend fun replaceCounts(rows: List<ModelCountEntity>) {
        clearCounts()
        if (rows.isNotEmpty()) insertCounts(rows)
    }

    // Predictions

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePrediction(prediction: PredictionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePredictions(predictions: List<PredictionEntity>)

    @Query("UPDATE predictions SET source = :source, filedCategoryId = :categoryId WHERE transactionId = :transactionId")
    suspend fun markFiled(transactionId: String, source: PredictionSource, categoryId: String)

    @Query("DELETE FROM predictions")
    suspend fun clearPredictions()

    /** Guesses about payments that are settled now: not waiting for review, not broken down. */
    @Query(
        """
        SELECT p.createdAt, p.source, p.filedCategoryId, p.modelCategoryId, t.categoryId AS finalCategoryId
        FROM predictions p
        JOIN transactions t ON t.id = p.transactionId
        WHERE t.deleted = 0 AND t.status != 'NEEDS_REVIEW'
          AND NOT EXISTS (SELECT 1 FROM transaction_items i WHERE i.transactionId = t.id)
        ORDER BY p.createdAt
        """,
    )
    fun observeOutcomes(): Flow<List<GuessOutcome>>

    // Recurring payments

    /** Spending payments since [sinceMillis], with the merchant's name. */
    @Query(
        """
        SELECT t.id, t.timestamp, t.amountPaise, t.categoryId, t.merchantId,
               COALESCE(m.displayName, t.payeeRaw, t.vpa) AS title, t.vpa
        FROM transactions t
        LEFT JOIN merchants m ON m.id = t.merchantId
        WHERE t.deleted = 0 AND t.type = 'EXPENSE' AND t.direction = 'DEBIT' AND t.timestamp >= :sinceMillis
        ORDER BY t.timestamp
        """,
    )
    suspend fun paymentsSince(sinceMillis: Long): List<PaymentRow>

    @Query("SELECT * FROM recurring ORDER BY nextExpectedAt")
    fun observeRecurring(): Flow<List<RecurringEntity>>

    @Query("DELETE FROM recurring")
    suspend fun clearRecurring()

    @Upsert
    suspend fun upsertRecurring(rows: List<RecurringEntity>)

    @Transaction
    suspend fun replaceRecurring(rows: List<RecurringEntity>) {
        clearRecurring()
        if (rows.isNotEmpty()) upsertRecurring(rows)
    }
}
