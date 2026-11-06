package com.mehul.inkless.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.mehul.inkless.data.db.entity.CategoryEntity
import com.mehul.inkless.data.db.entity.MerchantEntity
import com.mehul.inkless.data.db.entity.MyAccountEntity
import com.mehul.inkless.data.db.entity.RuleEntity
import com.mehul.inkless.data.db.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

/**
 * What the cloud backup needs from each synced table: rows with changes to upload, marking them
 * uploaded, and writing what came down from the cloud.
 *
 * A row is only marked synced if it hasn't changed since it was read for upload ([updatedAt]
 * still matches); an edit made during the upload keeps it DIRTY for the next round.
 */
@Dao
interface SyncDao {

    @Query(
        """
        SELECT (SELECT COUNT(*) FROM transactions WHERE syncState = 'DIRTY')
             + (SELECT COUNT(*) FROM categories WHERE syncState = 'DIRTY')
             + (SELECT COUNT(*) FROM merchants WHERE syncState = 'DIRTY')
             + (SELECT COUNT(*) FROM rules WHERE syncState = 'DIRTY')
             + (SELECT COUNT(*) FROM my_accounts WHERE syncState = 'DIRTY')
        """,
    )
    fun observePending(): Flow<Int>

    // Transactions

    @Query("SELECT * FROM transactions WHERE syncState = 'DIRTY' LIMIT :limit")
    suspend fun dirtyTransactions(limit: Int): List<TransactionEntity>

    @Query("UPDATE transactions SET syncState = 'SYNCED' WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun transactionSynced(id: String, updatedAt: Long)

    @Query("SELECT * FROM transactions WHERE id IN (:ids)")
    suspend fun transactions(ids: List<String>): List<TransactionEntity>

    @Upsert
    suspend fun saveTransactions(rows: List<TransactionEntity>)

    @Query("UPDATE transactions SET syncState = 'DIRTY'")
    suspend fun markTransactionsDirty()

    // Categories

    @Query("SELECT * FROM categories WHERE syncState = 'DIRTY' LIMIT :limit")
    suspend fun dirtyCategories(limit: Int): List<CategoryEntity>

    @Query("UPDATE categories SET syncState = 'SYNCED' WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun categorySynced(id: String, updatedAt: Long)

    @Query("SELECT * FROM categories WHERE id IN (:ids)")
    suspend fun categories(ids: List<String>): List<CategoryEntity>

    @Upsert
    suspend fun saveCategories(rows: List<CategoryEntity>)

    @Query("UPDATE categories SET syncState = 'DIRTY'")
    suspend fun markCategoriesDirty()

    // Merchants

    @Query("SELECT * FROM merchants WHERE syncState = 'DIRTY' LIMIT :limit")
    suspend fun dirtyMerchants(limit: Int): List<MerchantEntity>

    @Query("UPDATE merchants SET syncState = 'SYNCED' WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun merchantSynced(id: String, updatedAt: Long)

    @Query("SELECT * FROM merchants WHERE id IN (:ids)")
    suspend fun merchants(ids: List<String>): List<MerchantEntity>

    @Upsert
    suspend fun saveMerchants(rows: List<MerchantEntity>)

    @Query("UPDATE merchants SET syncState = 'DIRTY'")
    suspend fun markMerchantsDirty()

    // Rules

    @Query("SELECT * FROM rules WHERE syncState = 'DIRTY' LIMIT :limit")
    suspend fun dirtyRules(limit: Int): List<RuleEntity>

    @Query("UPDATE rules SET syncState = 'SYNCED' WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun ruleSynced(id: String, updatedAt: Long)

    @Query("SELECT * FROM rules WHERE id IN (:ids)")
    suspend fun rules(ids: List<String>): List<RuleEntity>

    @Upsert
    suspend fun saveRules(rows: List<RuleEntity>)

    @Query("UPDATE rules SET syncState = 'DIRTY'")
    suspend fun markRulesDirty()

    // My accounts

    @Query("SELECT * FROM my_accounts WHERE syncState = 'DIRTY' LIMIT :limit")
    suspend fun dirtyAccounts(limit: Int): List<MyAccountEntity>

    @Query("UPDATE my_accounts SET syncState = 'SYNCED' WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun accountSynced(id: String, updatedAt: Long)

    @Query("SELECT * FROM my_accounts WHERE id IN (:ids)")
    suspend fun accounts(ids: List<String>): List<MyAccountEntity>

    @Upsert
    suspend fun saveAccounts(rows: List<MyAccountEntity>)

    @Query("UPDATE my_accounts SET syncState = 'DIRTY'")
    suspend fun markAccountsDirty()
}
