package com.mehul.inkless.data.repo

import com.mehul.inkless.data.db.DatabaseProvider
import com.mehul.inkless.data.db.InklessDatabase
import com.mehul.inkless.data.db.dao.CategoryTotal
import com.mehul.inkless.data.db.dao.TransactionListItem
import com.mehul.inkless.data.db.entity.CaptureSource
import com.mehul.inkless.data.db.entity.CategoryEntity
import com.mehul.inkless.data.db.entity.Direction
import com.mehul.inkless.data.db.entity.RawCaptureEntity
import com.mehul.inkless.data.db.entity.SyncState
import com.mehul.inkless.data.db.entity.TransactionEntity
import com.mehul.inkless.data.db.entity.TransactionStatus
import com.mehul.inkless.data.db.entity.TransactionType
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

    /** Saves the user's edits; a category they picked themselves is no longer "needs review". */
    suspend fun edit(id: String, categoryId: String?, note: String?) {
        val dao = db().transactionDao()
        val current = dao.get(id) ?: return
        dao.update(
            current.copy(
                categoryId = categoryId,
                note = note?.trim()?.ifEmpty { null },
                status = TransactionStatus.CONFIRMED,
                confidence = 1f,
                updatedAt = clock.millis(),
                syncState = SyncState.DIRTY,
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

    fun unparsed(): Flow<List<RawCaptureEntity>> = fromCurrentDb { it.rawCaptureDao().observeUnparsed() }

    fun unparsedCount(): Flow<Int> = fromCurrentDb { it.rawCaptureDao().observeUnparsedCount() }

    suspend fun dismissUnparsed(id: String) = db().rawCaptureDao().delete(id)
}
