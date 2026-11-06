package com.mehul.inkless.export

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.mehul.inkless.data.db.InklessDatabase
import com.mehul.inkless.data.db.dao.SyncDao
import com.mehul.inkless.sync.SyncTable
import com.mehul.inkless.sync.SyncTables
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject

/** How much a restore brought in, per table ("transactions" → 213). */
data class RestoreResult(val added: Map<String, Int>) {
    val payments: Int get() = added["transactions"] ?: 0
    val total: Int get() = added.values.sum()
}

/**
 * Exports for the user: payments as CSV, everything as readable JSON, or everything as a
 * password-locked backup file that can be restored on any phone, into any account. Files are
 * written where the user picks with Android's file picker; the app needs no storage permission.
 */
class DataExporter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val clock: Clock,
) {
    suspend fun csv(db: InklessDatabase): String {
        val dao = db.syncDao()
        val payments = dao.allTransactions()
        val shares = db.friendDao().liveSplits().associate { split ->
            split.transactionId to db.friendDao().shares(split.id).sumOf { it.sharePaise }
        }
        return CsvExport.write(
            payments = payments,
            items = payments.map { it.id }.chunked(500).flatMap { dao.itemsOf(it) }.groupBy { it.transactionId },
            friendsShares = shares,
            categoryNames = dao.allCategories().associate { it.id to it.name },
            merchantNames = dao.allMerchants().associate { it.id to it.displayName },
            zone = ZoneId.systemDefault(),
        )
    }

    suspend fun json(db: InklessDatabase): String {
        val dao = db.syncDao()
        val tables = SyncTables.all.associate { table -> table.collection to rowsOf(table, dao) }
        return BackupFile.write(tables, clock.millis())
    }

    private suspend fun <E> rowsOf(table: SyncTable<E>, dao: SyncDao): List<BackupRow> =
        table.all(dao).map { BackupRow(table.id(it), table.toDoc(it)) }

    fun encrypt(json: String, password: CharArray): ByteArray = BackupCrypto.encrypt(json.toByteArray(), password)

    /**
     * Brings a backup into the open account: rows it doesn't have are added, and rows it has are
     * replaced only by a later edit, like sync. Everything restored is queued for the cloud backup.
     */
    suspend fun restore(db: InklessDatabase, file: ByteArray, password: CharArray?, ownerUid: String): RestoreResult {
        val text = if (BackupCrypto.isEncrypted(file)) {
            String(BackupCrypto.decrypt(file, password ?: throw WrongPassword()))
        } else {
            String(file)
        }
        val tables = BackupFile.read(text)
        val dao = db.syncDao()
        val added = mutableMapOf<String, Int>()
        db.withTransaction {
            // In sync order, so categories and merchants are in before the payments that use them.
            for (table in SyncTables.all) {
                val rows = tables[table.collection].orEmpty()
                val count = restoreTable(table, dao, rows, ownerUid)
                if (count > 0) added[table.collection] = count
            }
        }
        return RestoreResult(added)
    }

    private suspend fun <E> restoreTable(table: SyncTable<E>, dao: SyncDao, rows: List<BackupRow>, ownerUid: String): Int {
        val incoming = rows.mapNotNull { table.fromDoc(it.id, it.data, ownerUid) }
        if (incoming.isEmpty()) return 0
        // SQLite takes a few hundred ids per query at most.
        val local = incoming.map(table.id).chunked(500).flatMap { table.local(dao, it) }
        val winners = table.winners(incoming, local)
        if (winners.isEmpty()) return 0
        table.save(dao, winners)
        table.markAllDirty(dao)
        return winners.size
    }

    suspend fun writeTo(uri: Uri, bytes: ByteArray) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: error("Couldn't open the file")
    }

    suspend fun readFrom(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Couldn't open the file")
    }
}
