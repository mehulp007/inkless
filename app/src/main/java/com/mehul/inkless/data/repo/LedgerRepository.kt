package com.mehul.inkless.data.repo

import androidx.room.withTransaction
import com.mehul.inkless.breakdown.Breakdown
import com.mehul.inkless.breakdown.DraftItem
import com.mehul.inkless.capture.AccountReclassifier
import com.mehul.inkless.categorize.MerchantMemory
import com.mehul.inkless.categorize.Recategorizer
import com.mehul.inkless.categorize.RuleField
import com.mehul.inkless.categorize.RuleMatch
import com.mehul.inkless.data.db.DatabaseProvider
import com.mehul.inkless.data.db.InklessDatabase
import com.mehul.inkless.data.db.SpendPart
import com.mehul.inkless.data.db.dao.MerchantSpend
import com.mehul.inkless.data.db.entity.BudgetEntity
import com.mehul.inkless.data.db.entity.BudgetPeriod
import com.mehul.inkless.data.db.dao.CategoryTotal
import com.mehul.inkless.data.db.dao.TransactionListItem
import com.mehul.inkless.data.db.entity.CaptureSource
import com.mehul.inkless.data.db.entity.CategoryEntity
import com.mehul.inkless.data.db.entity.Direction
import com.mehul.inkless.data.db.entity.AccountMatch
import com.mehul.inkless.data.db.entity.AccountRole
import com.mehul.inkless.data.db.entity.MerchantEntity
import com.mehul.inkless.data.db.entity.MyAccountEntity
import com.mehul.inkless.data.db.entity.RawCaptureEntity
import com.mehul.inkless.data.db.entity.RuleEntity
import com.mehul.inkless.data.db.entity.SyncState
import com.mehul.inkless.data.db.entity.TransactionEntity
import com.mehul.inkless.data.db.entity.TransactionItemEntity
import com.mehul.inkless.data.db.entity.TransactionStatus
import com.mehul.inkless.data.db.entity.TransactionType
import com.mehul.inkless.data.seed.CategoryIds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class LedgerRepository @Inject constructor(
    private val databases: DatabaseProvider,
    private val recategorizer: Recategorizer,
    private val accountReclassifier: AccountReclassifier,
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

    /** Spending as categories and budgets count it; see [SpendPart]. */
    fun spendBetween(startMillis: Long, endMillis: Long): Flow<List<SpendPart>> =
        fromCurrentDb { it.spendDao().observeBetween(startMillis, endMillis) }

    fun topMerchants(categoryId: String, startMillis: Long, endMillis: Long, limit: Int): Flow<List<MerchantSpend>> =
        fromCurrentDb { it.spendDao().observeTopMerchants(categoryId, startMillis, endMillis, limit) }

    fun category(id: String): Flow<CategoryEntity?> = fromCurrentDb { it.categoryDao().observe(id) }

    /** Null clears the category's budget. */
    suspend fun setCategoryBudget(id: String, paise: Long?) = db().categoryDao().setBudget(id, paise, clock.millis())

    fun budget(): Flow<BudgetEntity?> = fromCurrentDb { it.budgetDao().observe() }

    suspend fun saveBudget(amountPaise: Long, allowanceDay: Int?, dailyFoodCapPaise: Long?, ownerUid: String) {
        db().budgetDao().save(
            BudgetEntity(
                id = BudgetEntity.MONTHLY_ID,
                period = BudgetPeriod.MONTH,
                amountPaise = amountPaise,
                allowanceDay = allowanceDay,
                dailyFoodCapPaise = dailyFoodCapPaise,
                ownerUid = ownerUid,
                updatedAt = clock.millis(),
            ),
        )
    }

    /** Stops budgeting; the row stays as a deletion so other phones stop too. */
    suspend fun removeBudget() {
        val db = db()
        val current = db.budgetDao().get() ?: return
        db.budgetDao().save(current.copy(deleted = true, updatedAt = clock.millis(), syncState = SyncState.DIRTY))
    }

    fun transaction(id: String): Flow<TransactionEntity?> =
        fromCurrentDb { it.transactionDao().observe(id) }

    fun items(transactionId: String): Flow<List<TransactionItemEntity>> =
        fromCurrentDb { it.transactionItemDao().observe(transactionId) }

    fun itemsBetween(startMillis: Long, endMillis: Long): Flow<List<TransactionItemEntity>> =
        fromCurrentDb { it.transactionItemDao().observeBetween(startMillis, endMillis) }

    /**
     * Saves a payment's breakdown, replacing any earlier one. The payment itself is filed under its
     * biggest item, which is the tab it shows in on Home. Merchant memory isn't touched: one shop
     * selling many kinds of things says nothing about the next payment there.
     */
    suspend fun saveBreakdown(transactionId: String, items: List<DraftItem>) {
        val db = db()
        val dao = db.transactionDao()
        val current = dao.get(transactionId) ?: return
        require(Breakdown.isValid(current.amountPaise, items.map { it.amountPaise })) { "Items don't add up to the payment" }
        require(db.friendDao().splitFor(transactionId)?.deleted != false) { "A split payment can't also be broken down" }
        val primary = checkNotNull(Breakdown.primary(items) { it.amountPaise })
        val now = clock.millis()
        db.withTransaction {
            db.transactionItemDao().replace(
                transactionId,
                items.mapIndexed { index, item ->
                    TransactionItemEntity(
                        id = UUID.randomUUID().toString(),
                        transactionId = transactionId,
                        amountPaise = item.amountPaise,
                        categoryId = checkNotNull(item.categoryId),
                        note = item.note?.trim()?.ifEmpty { null },
                        sortOrder = index,
                        ownerUid = current.ownerUid,
                    )
                },
            )
            dao.update(
                current.copy(
                    categoryId = primary.categoryId,
                    status = TransactionStatus.CONFIRMED,
                    confidence = 1f,
                    updatedAt = now,
                    syncState = SyncState.DIRTY,
                ),
            )
        }
    }

    /** Back to one category for the whole payment: the one its biggest item had. */
    suspend fun removeBreakdown(transactionId: String) {
        val db = db()
        val current = db.transactionDao().get(transactionId) ?: return
        db.withTransaction {
            db.transactionItemDao().replace(transactionId, emptyList())
            db.transactionDao().update(current.copy(updatedAt = clock.millis(), syncState = SyncState.DIRTY))
        }
    }

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
            // A broken-down payment says nothing about what this merchant usually is.
            val brokenDown = db.transactionItemDao().get(id).isNotEmpty()
            if (categoryId != null && categoryId != CategoryIds.OTHER && current.type != TransactionType.INCOME && !brokenDown) {
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
                ?.also { db.transactionDao().linkMerchant(txn.id, it.id, now) }?.id
            ?: return
        MerchantMemory.learn(db, merchantId, categoryId, now)
    }

    /** Spending in the last [days] days, newest first. */
    suspend fun recentSpending(days: Long, limit: Int): List<TransactionEntity> =
        db().transactionDao().recentSpending(clock.millis() - java.time.Duration.ofDays(days).toMillis(), limit)

    fun merchant(id: String): Flow<MerchantEntity?> = fromCurrentDb { it.merchantDao().observe(id) }

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

    fun myAccounts(): Flow<List<MyAccountEntity>> = fromCurrentDb { it.myAccountDao().observeAll() }

    suspend fun addMyAccount(match: AccountMatch, value: String, role: AccountRole, label: String?, ownerUid: String) {
        val db = db()
        db.myAccountDao().insert(
            MyAccountEntity(
                id = UUID.randomUUID().toString(),
                match = match,
                value = value.trim(),
                role = role,
                label = label?.trim()?.ifEmpty { null },
                ownerUid = ownerUid,
                updatedAt = clock.millis(),
            ),
        )
        reclassify(db, ownerUid)
    }

    suspend fun removeMyAccount(account: MyAccountEntity) {
        val db = db()
        db.myAccountDao().update(account.copy(deleted = true, updatedAt = clock.millis(), syncState = SyncState.DIRTY))
        reclassify(db, account.ownerUid)
    }

    private suspend fun reclassify(db: InklessDatabase, ownerUid: String) {
        accountReclassifier.run(db, clock.millis())
        // Payments that stopped being transfers go back through the categoriser.
        recategorizer.run(db, ownerUid)
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

    /** Hidden rather than removed, so the deletion reaches other devices. A split goes with it. */
    suspend fun delete(id: String) {
        val db = db()
        val dao = db.transactionDao()
        val current = dao.get(id) ?: return
        val now = clock.millis()
        db.withTransaction {
            dao.update(current.copy(deleted = true, updatedAt = now, syncState = SyncState.DIRTY))
            db.friendDao().splitFor(id)?.takeUnless { it.deleted }?.let { split ->
                db.friendDao().deleteShares(split.id)
                db.friendDao().saveSplit(split.copy(deleted = true, updatedAt = now, syncState = SyncState.DIRTY))
            }
        }
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
