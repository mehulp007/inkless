package com.mehul.inkless.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.mehul.inkless.data.db.entity.BudgetEntity
import com.mehul.inkless.data.db.entity.CategoryEntity
import com.mehul.inkless.data.db.entity.FriendEntity
import com.mehul.inkless.data.db.entity.IouEntity
import com.mehul.inkless.data.db.entity.MerchantEntity
import com.mehul.inkless.data.db.entity.MyAccountEntity
import com.mehul.inkless.data.db.entity.RuleEntity
import com.mehul.inkless.data.db.entity.SplitEntity
import com.mehul.inkless.data.db.entity.SplitShareEntity
import com.mehul.inkless.data.db.entity.TransactionEntity
import com.mehul.inkless.data.db.entity.TransactionItemEntity
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
             + (SELECT COUNT(*) FROM budgets WHERE syncState = 'DIRTY')
             + (SELECT COUNT(*) FROM friends WHERE syncState = 'DIRTY')
             + (SELECT COUNT(*) FROM splits WHERE syncState = 'DIRTY')
             + (SELECT COUNT(*) FROM ious WHERE syncState = 'DIRTY')
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

    /** Every row, deleted ones too, for the backup file. */
    @Query("SELECT * FROM transactions")
    suspend fun allTransactions(): List<TransactionEntity>

    @Upsert
    suspend fun saveTransactions(rows: List<TransactionEntity>)

    @Query("UPDATE transactions SET syncState = 'DIRTY'")
    suspend fun markTransactionsDirty()

    // A payment's items travel inside its document.

    @Query("SELECT * FROM transaction_items WHERE transactionId IN (:transactionIds) ORDER BY transactionId, sortOrder")
    suspend fun itemsOf(transactionIds: List<String>): List<TransactionItemEntity>

    @Query("DELETE FROM transaction_items WHERE transactionId IN (:transactionIds)")
    suspend fun deleteItemsOf(transactionIds: List<String>)

    @Insert
    suspend fun insertItems(items: List<TransactionItemEntity>)

    // Categories

    @Query("SELECT * FROM categories WHERE syncState = 'DIRTY' LIMIT :limit")
    suspend fun dirtyCategories(limit: Int): List<CategoryEntity>

    @Query("UPDATE categories SET syncState = 'SYNCED' WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun categorySynced(id: String, updatedAt: Long)

    @Query("SELECT * FROM categories WHERE id IN (:ids)")
    suspend fun categories(ids: List<String>): List<CategoryEntity>

    /** Every row, deleted ones too, for the backup file. */
    @Query("SELECT * FROM categories")
    suspend fun allCategories(): List<CategoryEntity>

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

    /** Every row, deleted ones too, for the backup file. */
    @Query("SELECT * FROM merchants")
    suspend fun allMerchants(): List<MerchantEntity>

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

    /** Every row, deleted ones too, for the backup file. */
    @Query("SELECT * FROM rules")
    suspend fun allRules(): List<RuleEntity>

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

    /** Every row, deleted ones too, for the backup file. */
    @Query("SELECT * FROM my_accounts")
    suspend fun allAccounts(): List<MyAccountEntity>

    @Upsert
    suspend fun saveAccounts(rows: List<MyAccountEntity>)

    @Query("UPDATE my_accounts SET syncState = 'DIRTY'")
    suspend fun markAccountsDirty()

    // Budgets

    @Query("SELECT * FROM budgets WHERE syncState = 'DIRTY' LIMIT :limit")
    suspend fun dirtyBudgets(limit: Int): List<BudgetEntity>

    @Query("UPDATE budgets SET syncState = 'SYNCED' WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun budgetSynced(id: String, updatedAt: Long)

    @Query("SELECT * FROM budgets WHERE id IN (:ids)")
    suspend fun budgets(ids: List<String>): List<BudgetEntity>

    /** Every row, deleted ones too, for the backup file. */
    @Query("SELECT * FROM budgets")
    suspend fun allBudgets(): List<BudgetEntity>

    @Upsert
    suspend fun saveBudgets(rows: List<BudgetEntity>)

    @Query("UPDATE budgets SET syncState = 'DIRTY'")
    suspend fun markBudgetsDirty()

    // Friends

    @Query("SELECT * FROM friends WHERE syncState = 'DIRTY' LIMIT :limit")
    suspend fun dirtyFriends(limit: Int): List<FriendEntity>

    @Query("UPDATE friends SET syncState = 'SYNCED' WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun friendSynced(id: String, updatedAt: Long)

    @Query("SELECT * FROM friends WHERE id IN (:ids)")
    suspend fun friendsById(ids: List<String>): List<FriendEntity>

    /** Every row, deleted ones too, for the backup file. */
    @Query("SELECT * FROM friends")
    suspend fun allFriends(): List<FriendEntity>

    @Upsert
    suspend fun saveFriends(rows: List<FriendEntity>)

    @Query("UPDATE friends SET syncState = 'DIRTY'")
    suspend fun markFriendsDirty()

    // Splits; their shares travel inside them

    @Query("SELECT * FROM splits WHERE syncState = 'DIRTY' LIMIT :limit")
    suspend fun dirtySplits(limit: Int): List<SplitEntity>

    @Query("UPDATE splits SET syncState = 'SYNCED' WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun splitSynced(id: String, updatedAt: Long)

    @Query("SELECT * FROM splits WHERE id IN (:ids)")
    suspend fun splitsById(ids: List<String>): List<SplitEntity>

    /** Every row, deleted ones too, for the backup file. */
    @Query("SELECT * FROM splits")
    suspend fun allSplits(): List<SplitEntity>

    @Upsert
    suspend fun saveSplits(rows: List<SplitEntity>)

    @Query("UPDATE splits SET syncState = 'DIRTY'")
    suspend fun markSplitsDirty()

    @Query("SELECT * FROM split_shares WHERE splitId IN (:splitIds)")
    suspend fun sharesOf(splitIds: List<String>): List<SplitShareEntity>

    @Query("DELETE FROM split_shares WHERE splitId IN (:splitIds)")
    suspend fun deleteSharesOf(splitIds: List<String>)

    @Insert
    suspend fun insertShares(shares: List<SplitShareEntity>)

    // IOUs

    @Query("SELECT * FROM ious WHERE syncState = 'DIRTY' LIMIT :limit")
    suspend fun dirtyIous(limit: Int): List<IouEntity>

    @Query("UPDATE ious SET syncState = 'SYNCED' WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun iouSynced(id: String, updatedAt: Long)

    @Query("SELECT * FROM ious WHERE id IN (:ids)")
    suspend fun iousById(ids: List<String>): List<IouEntity>

    /** Every row, deleted ones too, for the backup file. */
    @Query("SELECT * FROM ious")
    suspend fun allIous(): List<IouEntity>

    @Upsert
    suspend fun saveIous(rows: List<IouEntity>)

    @Query("UPDATE ious SET syncState = 'DIRTY'")
    suspend fun markIousDirty()
}
