package com.mehul.inkless.sync

import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.mehul.inkless.auth.await
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** A document as it came down from the cloud, with the server time it was last written. */
class RemoteDoc(val id: String, val data: Map<String, Any?>, val syncedAt: Long)

class OfflineException : IOException("No connection to the cloud")

/**
 * The account's cloud copy: one collection per synced table, plus a backup switch that every
 * device of the account follows. Calls throw [OfflineException] when the cloud can't be reached.
 */
interface CloudStore {
    suspend fun backupEnabled(uid: String): Boolean
    suspend fun setBackupEnabled(uid: String, enabled: Boolean)

    /** Overwrites each document and stamps it with the cloud's own clock. */
    suspend fun write(uid: String, collection: String, docs: List<Pair<String, Map<String, Any?>>>)

    /** Every document written after [sinceMillis] (cloud time), oldest first. Null reads everything. */
    suspend fun changedSince(uid: String, collection: String, sinceMillis: Long?): List<RemoteDoc>

    /** Deletes every synced document of the account. The profile and username stay. */
    suspend fun eraseAll(uid: String)
}

/** [CloudStore] in Firestore: `users/{uid}/<collection>/{id}`, with the switch on the profile `users/{uid}`. */
@Singleton
class FirestoreCloudStore @Inject constructor(
    private val firestore: FirebaseFirestore,
) : CloudStore {
    private fun user(uid: String) = firestore.collection("users").document(uid)

    // Accounts made before the switch existed have no field, and backup is on by default.
    override suspend fun backupEnabled(uid: String): Boolean =
        user(uid).get(Source.SERVER).awaitOnline().getBoolean(BACKUP_FIELD) ?: true

    override suspend fun setBackupEnabled(uid: String, enabled: Boolean) {
        user(uid).update(BACKUP_FIELD, enabled).awaitOnline()
    }

    override suspend fun write(uid: String, collection: String, docs: List<Pair<String, Map<String, Any?>>>) {
        val ref = user(uid).collection(collection)
        docs.chunked(BATCH_SIZE).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { (id, data) -> batch.set(ref.document(id), data + (CloudDocs.SYNCED_AT to FieldValue.serverTimestamp())) }
            batch.commit().awaitOnline()
        }
    }

    override suspend fun changedSince(uid: String, collection: String, sinceMillis: Long?): List<RemoteDoc> {
        var query = user(uid).collection(collection).orderBy(CloudDocs.SYNCED_AT).limit(PAGE_SIZE.toLong())
        if (sinceMillis != null) query = query.whereGreaterThan(CloudDocs.SYNCED_AT, timestampOf(sinceMillis))

        val docs = mutableListOf<RemoteDoc>()
        var last: DocumentSnapshot? = null
        do {
            val page = (last?.let(query::startAfter) ?: query).get(Source.SERVER).awaitOnline()
            for (doc in page.documents) {
                val syncedAt = doc.getTimestamp(CloudDocs.SYNCED_AT) ?: continue
                docs += RemoteDoc(doc.id, doc.data.orEmpty(), syncedAt.toDate().time)
            }
            last = page.documents.lastOrNull()
        } while (page.size() == PAGE_SIZE)
        return docs
    }

    override suspend fun eraseAll(uid: String) {
        for (collection in SyncTables.collections) {
            val ref = user(uid).collection(collection)
            while (true) {
                val page = ref.limit(BATCH_SIZE.toLong()).get(Source.SERVER).awaitOnline()
                if (page.isEmpty) break
                val batch = firestore.batch()
                page.documents.forEach { batch.delete(it.reference) }
                batch.commit().awaitOnline()
            }
        }
    }

    // Offline, Firestore queues a write and its task just waits for the network, so give up instead
    // and leave the rows dirty for the next try.
    // (Boxed because a commit's result is null even when it succeeds.)
    private suspend fun <T> Task<T>.awaitOnline(): T {
        val done = withTimeoutOrNull(NETWORK_TIMEOUT_MS) { Result.success(await()) } ?: throw OfflineException()
        return done.getOrThrow()
    }

    private fun timestampOf(millis: Long) = Timestamp(millis / 1000, ((millis % 1000) * 1_000_000).toInt())

    private companion object {
        const val BACKUP_FIELD = "backup"

        // Firestore allows 500 writes per batch.
        const val BATCH_SIZE = 400
        const val PAGE_SIZE = 500
        const val NETWORK_TIMEOUT_MS = 30_000L
    }
}
