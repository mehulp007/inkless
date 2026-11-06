package com.mehul.inkless.sync

import com.mehul.inkless.data.db.dao.SyncDao
import com.mehul.inkless.data.db.entity.SplitEntity
import com.mehul.inkless.data.db.entity.TransactionEntity

/**
 * One synced table: which Firestore collection it lives in, and how to read, map and write its
 * rows. Keeps the [Syncer] the same for every table instead of five copies of it.
 */
class SyncTable<E>(
    /** Collection under `users/{uid}/`. */
    val collection: String,
    val id: (E) -> String,
    val updatedAt: (E) -> Long,
    val toDoc: (E) -> Map<String, Any?>,
    val fromDoc: (id: String, data: Map<String, Any?>, ownerUid: String) -> E?,
    val dirty: suspend SyncDao.(limit: Int) -> List<E>,
    val markSynced: suspend SyncDao.(id: String, updatedAt: Long) -> Unit,
    val local: suspend SyncDao.(ids: List<String>) -> List<E>,
    val save: suspend SyncDao.(rows: List<E>) -> Unit,
    val markAllDirty: suspend SyncDao.() -> Unit,
    /** Puts back fields that never leave the phone when a row is replaced by the cloud's copy. */
    val keepLocal: (remote: E, local: E) -> E = { remote, _ -> remote },
) {
    /**
     * Last write wins: the cloud's rows that are new to the phone or edited later than the phone's
     * copy. A tie is the phone's own upload coming back, so it's left alone.
     */
    fun winners(remote: List<E>, local: List<E>): List<E> {
        val localById = local.associateBy(id)
        return remote.mapNotNull { row ->
            val mine = localById[id(row)]
            when {
                mine == null -> row
                updatedAt(row) > updatedAt(mine) -> keepLocal(row, mine)
                else -> null
            }
        }
    }
}

object SyncTables {

    internal val categories = SyncTable(
        collection = "categories",
        id = { it.id },
        updatedAt = { it.updatedAt },
        toDoc = CloudDocs::category,
        fromDoc = CloudDocs::category,
        dirty = { dirtyCategories(it) },
        markSynced = { id, at -> categorySynced(id, at) },
        local = { categories(it) },
        save = { saveCategories(it) },
        markAllDirty = { markCategoriesDirty() },
    )

    internal val merchants = SyncTable(
        collection = "merchants",
        id = { it.id },
        updatedAt = { it.updatedAt },
        toDoc = CloudDocs::merchant,
        fromDoc = CloudDocs::merchant,
        dirty = { dirtyMerchants(it) },
        markSynced = { id, at -> merchantSynced(id, at) },
        local = { merchants(it) },
        save = { saveMerchants(it) },
        markAllDirty = { markMerchantsDirty() },
    )

    internal val rules = SyncTable(
        collection = "rules",
        id = { it.id },
        updatedAt = { it.updatedAt },
        toDoc = CloudDocs::rule,
        fromDoc = CloudDocs::rule,
        dirty = { dirtyRules(it) },
        markSynced = { id, at -> ruleSynced(id, at) },
        local = { rules(it) },
        save = { saveRules(it) },
        markAllDirty = { markRulesDirty() },
    )

    internal val myAccounts = SyncTable(
        collection = "myAccounts",
        id = { it.id },
        updatedAt = { it.updatedAt },
        toDoc = CloudDocs::myAccount,
        fromDoc = CloudDocs::myAccount,
        dirty = { dirtyAccounts(it) },
        markSynced = { id, at -> accountSynced(id, at) },
        local = { accounts(it) },
        save = { saveAccounts(it) },
        markAllDirty = { markAccountsDirty() },
    )

    internal val budgets = SyncTable(
        collection = "budgets",
        id = { it.id },
        updatedAt = { it.updatedAt },
        toDoc = CloudDocs::budget,
        fromDoc = CloudDocs::budget,
        dirty = { dirtyBudgets(it) },
        markSynced = { id, at -> budgetSynced(id, at) },
        local = { budgets(it) },
        save = { saveBudgets(it) },
        markAllDirty = { markBudgetsDirty() },
    )

    internal val friends = SyncTable(
        collection = "friends",
        id = { it.id },
        updatedAt = { it.updatedAt },
        toDoc = CloudDocs::friend,
        fromDoc = CloudDocs::friend,
        dirty = { dirtyFriends(it) },
        markSynced = { id, at -> friendSynced(id, at) },
        local = { friendsById(it) },
        save = { saveFriends(it) },
        markAllDirty = { markFriendsDirty() },
    )

    internal val splits = SyncTable(
        collection = "splits",
        id = { it.split.id },
        updatedAt = { it.split.updatedAt },
        toDoc = { it.toMap() },
        fromDoc = SplitDoc::fromMap,
        dirty = { withShares(dirtySplits(it)) },
        markSynced = { id, at -> splitSynced(id, at) },
        local = { withShares(splitsById(it)) },
        save = { docs ->
            saveSplits(docs.map { it.split })
            deleteSharesOf(docs.map { it.split.id })
            insertShares(docs.flatMap { it.shares })
        },
        markAllDirty = { markSplitsDirty() },
        // Which shared document it went out as is this phone's business; keep it if the cloud copy has none.
        keepLocal = { remote, local -> remote.copy(split = remote.split.copy(sharedSplitId = remote.split.sharedSplitId ?: local.split.sharedSplitId)) },
    )

    internal val ious = SyncTable(
        collection = "ious",
        id = { it.id },
        updatedAt = { it.updatedAt },
        toDoc = CloudDocs::iou,
        fromDoc = CloudDocs::iou,
        dirty = { dirtyIous(it) },
        markSynced = { id, at -> iouSynced(id, at) },
        local = { iousById(it) },
        save = { saveIous(it) },
        markAllDirty = { markIousDirty() },
    )

    internal val transactions = SyncTable(
        collection = "transactions",
        id = { it.transaction.id },
        updatedAt = { it.transaction.updatedAt },
        toDoc = { it.toMap() },
        fromDoc = TransactionDoc::fromMap,
        dirty = { withItems(dirtyTransactions(it)) },
        markSynced = { id, at -> transactionSynced(id, at) },
        local = { withItems(transactions(it)) },
        save = { docs ->
            saveTransactions(docs.map { it.transaction })
            deleteItemsOf(docs.map { it.transaction.id })
            insertItems(docs.flatMap { it.items })
        },
        markAllDirty = { markTransactionsDirty() },
        // A receipt photo is a file on this phone; the cloud copy doesn't carry it.
        keepLocal = { remote, local -> remote.copy(transaction = remote.transaction.copy(receiptUri = local.transaction.receiptUri)) },
    )

    /** In upload and download order: what a transaction points at arrives before the transaction. */
    val all: List<SyncTable<*>> = listOf(categories, merchants, rules, myAccounts, budgets, friends, transactions, splits, ious)

    val collections: List<String> = all.map { it.collection }
}

private suspend fun SyncDao.withItems(transactions: List<TransactionEntity>): List<TransactionDoc> {
    val items = itemsOf(transactions.map { it.id }).groupBy { it.transactionId }
    return transactions.map { TransactionDoc(it, items[it.id].orEmpty()) }
}

private suspend fun SyncDao.withShares(splits: List<SplitEntity>): List<SplitDoc> {
    val shares = sharesOf(splits.map { it.id }).groupBy { it.splitId }
    return splits.map { SplitDoc(it, shares[it.id].orEmpty()) }
}
