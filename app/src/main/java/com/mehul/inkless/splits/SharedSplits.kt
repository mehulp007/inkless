package com.mehul.inkless.splits

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.mehul.inkless.auth.await
import com.mehul.inkless.data.db.InklessDatabase
import com.mehul.inkless.data.db.entity.IncomingSplitEntity
import com.mehul.inkless.data.db.entity.SplitEntity
import com.mehul.inkless.data.db.entity.SyncState
import com.mehul.inkless.sync.OfflineException
import com.mehul.inkless.ui.format.Money
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Splits with friends who use Inkless too, through `sharedSplits/{id}`:
 * `{ memberUids, payerUid, total, shares{uid: paise}, settled{uid: bool}, names{uid: username}, note, createdAt }`.
 * The payer's phone writes it from the local split; members' phones read it into their own
 * "shared with you" list, and each member can tick their own share as paid. This runs whether or
 * not cloud backup is on: it's the part of the app that's about other people.
 */
@Singleton
class SharedSplits @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val clock: Clock,
) {
    private val collection get() = firestore.collection("sharedSplits")

    /** Writes the user's own shared splits, then reads everyone's that include them. */
    suspend fun sync(uid: String, username: String?, db: InklessDatabase) {
        push(uid, username, db)
        pull(uid, db)
    }

    /** The user, as a member, has paid their part of someone else's split. */
    suspend fun markMyShareSettled(id: String) {
        val uid = auth.currentUser?.uid ?: return
        // Offline, Firestore would wait for the network forever; say so instead.
        withTimeoutOrNull(NETWORK_TIMEOUT_MS) { collection.document(id).update("settled.$uid", true).await(); true }
            ?: throw OfflineException()
    }

    /** Account deletion: removes the splits the user created; ones they're only a member of aren't theirs to remove. */
    suspend fun deleteMine(uid: String) {
        val mine = collection.whereArrayContains(MEMBERS, uid).get(Source.SERVER).await().documents
            .filter { it.getString(PAYER) == uid }
        mine.forEach { it.reference.delete().await() }
    }

    private suspend fun push(uid: String, username: String?, db: InklessDatabase) {
        val dao = db.friendDao()
        for (split in dao.deletedSharedSplits()) {
            collection.document(split.sharedSplitId!!).delete().await()
            dao.saveSplit(split.copy(sharedSplitId = null))
        }
        val friends = dao.friends().associateBy { it.id }
        for (split in dao.liveSplits()) {
            val txn = db.transactionDao().get(split.transactionId) ?: continue
            val linked = dao.shares(split.id).mapNotNull { share ->
                friends[share.friendId]?.takeIf { it.linkedUid != null }?.let { it to share }
            }
            if (linked.isEmpty()) {
                split.sharedSplitId?.let {
                    collection.document(it).delete().await()
                    detach(db, split)
                }
                continue
            }
            val friendsTotal = dao.shares(split.id).sumOf { it.sharePaise }
            val shares = mapOf(uid to txn.amountPaise - friendsTotal) + linked.associate { (f, s) -> f.linkedUid!! to s.sharePaise }
            val settled = mapOf(uid to true) + linked.associate { (f, s) -> f.linkedUid!! to s.settled }
            val names = mapOf(uid to (username ?: "A friend")) + linked.associate { (f, _) -> f.linkedUid!! to (f.linkedUsername ?: f.name) }
            val title = db.merchantDao().let { m -> txn.merchantId?.let { m.get(it)?.displayName } } ?: txn.payeeRaw ?: txn.vpa ?: "Payment"
            val note = "$title · ${Instant.ofEpochMilli(txn.timestamp).atZone(ZoneId.systemDefault()).format(DAY)}"

            val ref = collection.document(split.sharedSplitId ?: split.id)
            val current = ref.get(Source.SERVER).await()
            @Suppress("UNCHECKED_CAST")
            val currentShares = (current.get(SHARES) as? Map<String, Number>)?.mapValues { it.value.toLong() }
            when {
                // Amounts or people changed: shares can't be edited in place, so the split is written afresh.
                !current.exists() || currentShares != shares -> {
                    if (current.exists()) ref.delete().await()
                    ref.set(
                        mapOf(
                            MEMBERS to shares.keys.toList(),
                            PAYER to uid,
                            "total" to txn.amountPaise,
                            SHARES to shares,
                            SETTLED to settled,
                            NAMES to names,
                            "note" to note,
                            "createdAt" to FieldValue.serverTimestamp(),
                        ),
                    ).await()
                    if (split.sharedSplitId != ref.id) dao.saveSplit(split.copy(sharedSplitId = ref.id))
                }
                current.get(SETTLED) != settled -> {
                    // Ticked on either side: settled on one is settled on both.
                    @Suppress("UNCHECKED_CAST")
                    val theirs = current.get(SETTLED) as? Map<String, Boolean> ?: emptyMap()
                    val merged = settled.mapValues { (member, mine) -> mine || theirs[member] == true }
                    if (merged != theirs) ref.update(SETTLED, merged).await()
                }
            }
        }
    }

    private suspend fun pull(uid: String, db: InklessDatabase) {
        val dao = db.friendDao()
        val docs = collection.whereArrayContains(MEMBERS, uid).get(Source.SERVER).await().documents
        val incoming = mutableListOf<IncomingSplitEntity>()
        val friendsByUid = dao.friends().filter { it.linkedUid != null }.associateBy { it.linkedUid!! }
        for (doc in docs) {
            @Suppress("UNCHECKED_CAST")
            val settled = doc.get(SETTLED) as? Map<String, Boolean> ?: emptyMap()
            val payer = doc.getString(PAYER) ?: continue
            if (payer == uid) {
                // A friend ticked their share as paid in their app.
                val split = dao.split(doc.id) ?: dao.liveSplits().firstOrNull { it.sharedSplitId == doc.id } ?: continue
                val ticked = dao.shares(split.id).filter { share ->
                    val friendUid = friendsByUid.values.firstOrNull { it.id == share.friendId }?.linkedUid
                    !share.settled && friendUid != null && settled[friendUid] == true
                }
                ticked.forEach { dao.setShareSettled(it.id, true, null) }
                // So the backup copy of the split hears about it too.
                if (ticked.isNotEmpty()) dao.saveSplit(split.copy(updatedAt = clock.millis(), syncState = SyncState.DIRTY))
                continue
            }
            @Suppress("UNCHECKED_CAST")
            val shares = doc.get(SHARES) as? Map<String, Number> ?: continue
            @Suppress("UNCHECKED_CAST")
            val names = doc.get(NAMES) as? Map<String, String> ?: emptyMap()
            incoming += IncomingSplitEntity(
                id = doc.id,
                payerUid = payer,
                payerName = friendsByUid[payer]?.name ?: names[payer] ?: "A friend",
                totalPaise = doc.getLong("total") ?: continue,
                mySharePaise = shares[uid]?.toLong() ?: continue,
                settled = settled[uid] == true,
                note = doc.getString("note"),
                createdAt = doc.getTimestamp("createdAt")?.toDate()?.time ?: 0,
            )
        }
        dao.replaceIncoming(incoming)
    }

    private suspend fun detach(db: InklessDatabase, split: SplitEntity) =
        db.friendDao().saveSplit(split.copy(sharedSplitId = null, syncState = SyncState.DIRTY))

    /** "Rohan paid ₹600 for Swiggy · 3 Nov: your share ₹200" */
    fun describe(split: IncomingSplitEntity) =
        "${split.payerName} paid ${Money.format(split.totalPaise)}" + (split.note?.let { " for $it" } ?: "")

    private companion object {
        const val MEMBERS = "memberUids"
        const val PAYER = "payerUid"
        const val SHARES = "shares"
        const val SETTLED = "settled"
        const val NAMES = "names"
        const val NETWORK_TIMEOUT_MS = 20_000L
        val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
    }
}
