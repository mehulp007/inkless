package com.mehul.inkless.data.repo

import androidx.room.withTransaction
import com.mehul.inkless.categorize.MerchantMemory
import com.mehul.inkless.categorize.Recategorizer
import com.mehul.inkless.categorize.RuleField
import com.mehul.inkless.categorize.RuleMatch
import com.mehul.inkless.data.db.DatabaseProvider
import com.mehul.inkless.data.db.InklessDatabase
import com.mehul.inkless.data.db.dao.CategoryTotal
import com.mehul.inkless.data.db.dao.TransactionListItem
import com.mehul.inkless.data.db.entity.CaptureSource
import com.mehul.inkless.data.db.entity.CategoryEntity
import com.mehul.inkless.data.db.entity.Direction
import com.mehul.inkless.data.db.entity.MerchantEntity
import com.mehul.inkless.data.db.entity.RawCaptureEntity
import com.mehul.inkless.data.db.entity.RuleEntity
import com.mehul.inkless.data.db.entity.SyncState
import com.mehul.inkless.data.db.entity.TransactionEntity
import com.mehul.inkless.data.db.entity.TransactionStatus
import com.mehul.inkless.data.db.entity.TransactionType
import com.mehul.inkless.data.seed.CategoryIds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class LedgerRepository @Inject constructor(
    private val databases: DatabaseProvider,
    private val recategorizer: Recategorizer,
    private val clock: Clock,
) {
    // Re-subscribes whenever a different user's database is opened, and goes quiet when it's closed.
    private fun <T> fromCurrentDb(query: (InklessDatabase) -> Flow<T>): Flow<T> =
        databases.current.flatMapLatest { db -> if (db == null) emptyFlow() else query(db) }

    private fun db(): InklessDatabase = checkNotNull(databases.current.value) { "No database open" }

    fun categories(): Flow<List<CategoryEntity>> =
        fromCurrentDb { it.categoryDao().observeAll() }

    fun transactionsBetween(startMillis: Long, endMillis: Long): Flow<List<TransactionListItem>> =
        fromCurrentDb { it.transactionDao().observeBetween(startMillis, endMillis) }

    fun categoryTotalsBetween(startMillis: Long, endMillis: Long): Flow<List<CategoryTotal>> =
        fromCurrentDb { it.transactionDao().observeCategoryTotals(startMillis, endMillis) }

    fun transaction(id: String): Flow<TransactionEntity?> =
        fromCurrentDb { it.transactionDao().observe(id) }

    /**
     * Saves the user's edits. A category they picked themselves is no longer "needs review", and
     * the merchant remembers it, so its other waiting payments are filed too.
     */
    suspend fun edit(id: String, categoryId: String?, note: String?) {
        val db = db()
        val dao = db.transactionDao()
        val current = dao.get(id) ?: return
        val now = clock.millis()
        db.withTransaction {
            dao.update(
                current.copy(
                    categoryId = categoryId,
                    note = note?.trim()?.ifEmpty { null },
                    status = TransactionStatus.CONFIRMED,
                    confidence = 1f,
                    updatedAt = now,
                    syncState = SyncState.DIRTY,
                ),
            )
            if (categoryId != null && categoryId != CategoryIds.OTHER && current.type != TransactionType.INCOME) {
                rememberFor(db, current, categoryId, now)
            }
        }
    }

    /** One-tap categorising from the review inbox or the notification. */
    suspend fun categorize(id: String, categoryId: String) {
        val current = db().transactionDao().get(id) ?: return
        edit(id, categoryId, current.note)
    }

    /** Keeps the category but marks it as checked, for payments that really were "Other". */
    suspend fun keepAsIs(id: String) {
        val current = db().transactionDao().get(id) ?: return
        db().transactionDao().update(
            current.copy(status = TransactionStatus.CONFIRMED, updatedAt = clock.millis(), syncState = SyncState.DIRTY),
        )
    }

    private suspend fun rememberFor(db: InklessDatabase, txn: TransactionEntity, categoryId: String, now: Long) {
        val merchantId = txn.merchantId
            ?: MerchantMemory.findOrCreate(db, txn.vpa, txn.payeeRaw, txn.ownerUid, now)
                ?.also { db.transactionDao().linkMerchant(txn.id, it.id) }?.id
            ?: return
        MerchantMemory.learn(db, merchantId, categoryId, now)
    }

    fun merchant(id: String): Flow<MerchantEntity?> = fromCurrentDb { db -> flow { emit(db.merchantDao().get(id)) } }

    suspend fun renameMerchant(id: String, name: String) {
        val trimmed = name.trim().take(40).ifEmpty { return }
        db().merchantDao().rename(id, trimmed, clock.millis())
    }

    fun needsReview(): Flow<List<TransactionListItem>> = fromCurrentDb { it.transactionDao().observeNeedsReview() }

    /** The user's most-used categories lately, padded with sensible defaults. */
    suspend fun quickPicks(count: Int): List<String> {
        val since = clock.millis() - QUICK_PICK_WINDOW_MS
        val used = db().transactionDao().mostUsedCategories(since, count)
        return (used + DEFAULT_QUICK_PICKS).distinct().take(count)
    }

    fun rules(): Flow<List<RuleEntity>> = fromCurrentDb { it.ruleDao().observeAll() }

    /** Adds a rule after the existing ones and applies it to everything still waiting. Returns how many got filed. */
    suspend fun addRule(field: RuleField, match: RuleMatch, pattern: String, categoryId: String, ownerUid: String): Int {
        val db = db()
        val dao = db.ruleDao()
        dao.insert(
            RuleEntity(
                id = UUID.randomUUID().toString(),
                field = field,
                match = match,
                pattern = pattern.trim(),
                categoryId = categoryId,
                priority = dao.maxPriority() + 1,
                ownerUid = ownerUid,
                updatedAt = clock.millis(),
            ),
        )
        return recategorizer.run(db, ownerUid)
    }

    suspend fun deleteRule(rule: RuleEntity) {
        db().ruleDao().update(listOf(rule.copy(deleted = true, updatedAt = clock.millis(), syncState = SyncState.DIRTY)))
    }

    /** Swaps a rule with its neighbour, so it runs earlier ([up]) or later. */
    suspend fun moveRule(rule: RuleEntity, up: Boolean) {
        val dao = db().ruleDao()
        val all = dao.getAll()
        val index = all.indexOfFirst { it.id == rule.id }
        val other = all.getOrNull(if (up) index - 1 else index + 1) ?: return
        val now = clock.millis()
        dao.update(
            listOf(
                rule.copy(priority = other.priority, updatedAt = now, syncState = SyncState.DIRTY),
                other.copy(priority = rule.priority, updatedAt = now, syncState = SyncState.DIRTY),
            ),
        )
    }

    /** Hidden rather than removed, so the deletion can reach other devices once sync exists. */
    suspend fun delete(id: String) {
        val dao = db().transactionDao()
        val current = dao.get(id) ?: return
        dao.update(current.copy(deleted = true, updatedAt = clock.millis(), syncState = SyncState.DIRTY))
    }

    suspend fun addCash(amountPaise: Long, categoryId: String, note: String?, ownerUid: String) {
        val now = clock.millis()
        db().transactionDao().insert(
            TransactionEntity(
                id = UUID.randomUUID().toString(),
                amountPaise = amountPaise,
                direction = Direction.DEBIT,
                type = TransactionType.EXPENSE,
                timestamp = now,
                payeeRaw = note?.trim()?.ifEmpty { null } ?: "Cash",
                categoryId = categoryId,
                status = TransactionStatus.CONFIRMED,
                sources = setOf(CaptureSource.MANUAL),
                note = note?.trim()?.ifEmpty { null },
                createdAt = now,
                updatedAt = now,
                ownerUid = ownerUid,
            ),
        )
    }

    private companion object {
        val QUICK_PICK_WINDOW_MS = java.time.Duration.ofDays(90).toMillis()
        val DEFAULT_QUICK_PICKS = listOf(CategoryIds.FOOD_CANTEEN, CategoryIds.FOOD_DELIVERY, CategoryIds.TRAVEL, CategoryIds.GROCERIES)
    }

    fun unparsed(): Flow<List<RawCaptureEntity>> = fromCurrentDb { it.rawCaptureDao().observeUnparsed() }

    fun unparsedCount(): Flow<Int> = fromCurrentDb { it.rawCaptureDao().observeUnparsedCount() }

    suspend fun dismissUnparsed(id: String) = db().rawCaptureDao().delete(id)
}
