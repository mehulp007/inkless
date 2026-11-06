package com.mehul.inkless.sync

import com.mehul.inkless.data.db.dao.SyncDao

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

    internal val transactions = SyncTable(
        collection = "transactions",
        id = { it.id },
        updatedAt = { it.updatedAt },
        toDoc = { TransactionDoc(it).toMap() },
        fromDoc = { id, data, owner -> TransactionDoc.fromMap(id, data, owner)?.transaction },
        dirty = { dirtyTransactions(it) },
        markSynced = { id, at -> transactionSynced(id, at) },
        local = { transactions(it) },
        save = { saveTransactions(it) },
        markAllDirty = { markTransactionsDirty() },
        // A receipt photo is a file on this phone; the cloud copy doesn't carry it.
        keepLocal = { remote, local -> remote.copy(receiptUri = local.receiptUri) },
    )

    /** In upload and download order: what a transaction points at arrives before the transaction. */
    val all: List<SyncTable<*>> = listOf(categories, merchants, rules, myAccounts, transactions)

    val collections: List<String> = all.map { it.collection }
}
