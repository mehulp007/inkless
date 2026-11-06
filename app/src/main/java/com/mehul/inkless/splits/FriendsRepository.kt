package com.mehul.inkless.splits

import androidx.room.withTransaction
import com.mehul.inkless.auth.UsernameService
import com.mehul.inkless.data.db.DatabaseProvider
import com.mehul.inkless.data.db.InklessDatabase
import com.mehul.inkless.data.db.entity.FriendEntity
import com.mehul.inkless.data.db.entity.IncomingSplitEntity
import com.mehul.inkless.data.db.entity.IouEntity
import com.mehul.inkless.data.db.entity.SplitEntity
import com.mehul.inkless.data.db.entity.SplitMethod
import com.mehul.inkless.data.db.entity.SplitShareEntity
import com.mehul.inkless.data.db.entity.SyncState
import com.mehul.inkless.data.db.entity.TransactionStatus
import com.mehul.inkless.data.db.entity.TransactionType
import com.mehul.inkless.sync.SyncScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** A payment's split as the split screen shows it. */
data class SplitView(val split: SplitEntity, val shares: List<SplitShareEntity>)

class FriendNotFound : Exception("No Inkless user with that username")

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class FriendsRepository @Inject constructor(
    private val databases: DatabaseProvider,
    private val usernames: UsernameService,
    private val sharedSplits: SharedSplits,
    private val syncScheduler: SyncScheduler,
    private val clock: Clock,
) {
    private fun <T> fromCurrentDb(query: (InklessDatabase) -> Flow<T>): Flow<T> =
        databases.current.flatMapLatest { db -> if (db == null) emptyFlow() else query(db) }

    private fun db(): InklessDatabase = checkNotNull(databases.current.value) { "No database open" }

    fun friends(): Flow<List<FriendEntity>> = fromCurrentDb { it.friendDao().observeFriends() }

    fun friend(id: String): Flow<FriendEntity?> = fromCurrentDb { it.friendDao().observeFriend(id) }

    fun balances(): Flow<List<FriendBalance>> = fromCurrentDb { db ->
        val dao = db.friendDao()
        combine(dao.observeFriends(), dao.observeShareLines(), dao.observeIous(), dao.observeIncoming(), Balances::of)
    }

    fun incoming(): Flow<List<IncomingSplitEntity>> = fromCurrentDb { it.friendDao().observeIncoming() }

    /** Fetches splits friends shared, e.g. when the friends screen opens. */
    fun refresh() = syncScheduler.syncSoon()

    fun split(transactionId: String): Flow<SplitView?> = fromCurrentDb { db ->
        db.friendDao().observeSplitFor(transactionId).flatMapLatest { split ->
            if (split == null) flowOf(null) else db.friendDao().observeShares(split.id).let { shares ->
                combine(flowOf(split), shares, ::SplitView)
            }
        }
    }

    /**
     * Adds or edits a friend. [username] links them to their Inkless account so splits show up in
     * their app too; it's looked up online, and [FriendNotFound] means nobody has it.
     */
    suspend fun saveFriend(id: String?, name: String, vpas: List<String>, username: String?, ownerUid: String): String {
        val wanted = username?.trim()?.removePrefix("@")?.lowercase()?.ifEmpty { null }
        val existing = id?.let { db().friendDao().friend(it) }
        val linkedUid = when {
            wanted == null -> null
            wanted == existing?.linkedUsername -> existing.linkedUid
            else -> usernames.uidOf(wanted) ?: throw FriendNotFound()
        }
        val friend = FriendEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            name = name.trim(),
            vpas = vpas.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct(),
            linkedUid = linkedUid,
            linkedUsername = wanted.takeIf { linkedUid != null },
            ownerUid = ownerUid,
            updatedAt = clock.millis(),
        )
        db().friendDao().saveFriend(friend)
        return friend.id
    }

    suspend fun removeFriend(friend: FriendEntity) {
        db().friendDao().saveFriend(friend.copy(deleted = true, updatedAt = clock.millis(), syncState = SyncState.DIRTY))
    }

    /** Splits a payment between the user and friends. A payment is either split or broken down, not both. */
    suspend fun saveSplit(transactionId: String, method: SplitMethod, shares: List<ShareDraft>, ownerUid: String) {
        val db = db()
        val txn = db.transactionDao().get(transactionId) ?: return
        require(db.transactionItemDao().get(transactionId).isEmpty()) { "A broken-down payment can't also be split" }
        require(shares.isNotEmpty() && shares.sumOf { it.sharePaise } <= txn.amountPaise) { "Shares don't fit the payment" }
        val dao = db.friendDao()
        val now = clock.millis()
        db.withTransaction {
            val existing = dao.splitFor(transactionId)
            val split = SplitEntity(
                id = existing?.id ?: UUID.randomUUID().toString(),
                transactionId = transactionId,
                method = method,
                sharedSplitId = existing?.sharedSplitId,
                ownerUid = ownerUid,
                updatedAt = now,
            )
            // Settled stays settled for a friend whose amount didn't change.
            val before = existing?.let { dao.shares(it.id) }.orEmpty().associateBy { it.friendId }
            dao.saveSplit(split)
            dao.replaceShares(
                split.id,
                shares.map { draft ->
                    val old = before[draft.friendId]?.takeIf { it.sharePaise == draft.sharePaise }
                    SplitShareEntity(
                        id = UUID.randomUUID().toString(),
                        splitId = split.id,
                        friendId = draft.friendId,
                        sharePaise = draft.sharePaise,
                        basisPoints = draft.basisPoints,
                        settled = old?.settled ?: false,
                        settledByTxnId = old?.settledByTxnId,
                        ownerUid = ownerUid,
                    )
                },
            )
        }
        syncScheduler.syncSoon()
    }

    suspend fun removeSplit(transactionId: String) {
        val db = db()
        val dao = db.friendDao()
        val split = dao.splitFor(transactionId) ?: return
        db.withTransaction {
            dao.deleteShares(split.id)
            dao.saveSplit(split.copy(deleted = true, updatedAt = clock.millis(), syncState = SyncState.DIRTY))
        }
        syncScheduler.syncSoon()
    }

    suspend fun addIou(friendId: String, amountPaise: Long, theyOwe: Boolean, note: String?, ownerUid: String) {
        val now = clock.millis()
        db().friendDao().saveIou(
            IouEntity(
                id = UUID.randomUUID().toString(),
                friendId = friendId,
                amountPaise = amountPaise,
                theyOwe = theyOwe,
                note = note?.trim()?.ifEmpty { null },
                timestamp = now,
                ownerUid = ownerUid,
                updatedAt = now,
            ),
        )
    }

    /**
     * Marks shares and IOUs as paid back. With [byTransactionId] (the friend's repayment), that
     * payment becomes a settlement: money back, not income.
     */
    suspend fun settle(targets: List<SettleTarget>, byTransactionId: String?) {
        val db = db()
        val dao = db.friendDao()
        val now = clock.millis()
        db.withTransaction {
            for (target in targets) when (target) {
                is SettleTarget.Share -> {
                    dao.setShareSettled(target.line.shareId, true, byTransactionId)
                    dao.split(target.line.splitId)?.let { dao.saveSplit(it.copy(updatedAt = now, syncState = SyncState.DIRTY)) }
                }
                is SettleTarget.Iou -> dao.iou(target.iou.id)?.let {
                    dao.saveIou(it.copy(settled = true, settledByTxnId = byTransactionId, updatedAt = now, syncState = SyncState.DIRTY))
                }
            }
            byTransactionId?.let { id ->
                db.transactionDao().get(id)?.let { credit ->
                    db.transactionDao().update(
                        credit.copy(
                            type = TransactionType.SPLIT_SETTLEMENT,
                            categoryId = null,
                            status = TransactionStatus.CONFIRMED,
                            confidence = 1f,
                            updatedAt = now,
                            syncState = SyncState.DIRTY,
                        ),
                    )
                }
            }
        }
        syncScheduler.syncSoon()
    }

    /** Everything the friend owes, marked as paid (cash, or a repayment Inkless didn't see). */
    suspend fun settleAll(friendId: String) {
        val dao = db().friendDao()
        val targets = dao.openShares(friendId).map { SettleTarget.Share(it) } +
            dao.openIous(friendId).map { SettleTarget.Iou(it) }
        settle(targets, byTransactionId = null)
    }

    /** The user paid a friend back for a split they made; ticks it in the friend's app as well. */
    suspend fun markIncomingPaid(id: String) {
        sharedSplits.markMyShareSettled(id)
        db().friendDao().setIncomingSettled(id, true)
    }
}
