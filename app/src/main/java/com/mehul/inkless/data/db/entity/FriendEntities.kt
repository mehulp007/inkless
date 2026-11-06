package com.mehul.inkless.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Someone the user splits bills and lends money with. */
@Entity(tableName = "friends")
data class FriendEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** Their UPI IDs, so a repayment from them can be recognised. */
    val vpas: List<String> = emptyList(),
    /** Set when they use Inkless too; splits with them are then shared with their app. */
    val linkedUid: String? = null,
    val linkedUsername: String? = null,
    val ownerUid: String,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val syncState: SyncState = SyncState.DIRTY,
)

enum class SplitMethod { EQUAL, EXACT, PERCENT }

/**
 * A payment the user made for themselves and friends. Only their own part counts as their
 * spending; the friends' [SplitShareEntity] parts are money owed back. The shares travel inside
 * the split's cloud document, so they have no sync state of their own.
 */
@Entity(tableName = "splits", indices = [Index(value = ["transactionId"], unique = true)])
data class SplitEntity(
    @PrimaryKey val id: String,
    val transactionId: String,
    val method: SplitMethod,
    /** The `sharedSplits` document friends who use Inkless see, once it's been created. */
    val sharedSplitId: String? = null,
    val ownerUid: String,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val syncState: SyncState = SyncState.DIRTY,
)

/** One friend's part of a split. The user's own part is whatever the friends' parts leave. */
@Entity(tableName = "split_shares", indices = [Index("splitId"), Index("friendId")])
data class SplitShareEntity(
    @PrimaryKey val id: String,
    val splitId: String,
    val friendId: String,
    val sharePaise: Long,
    /** For a percentage split, the percentage in hundredths (2500 = 25%), to show it back. */
    val basisPoints: Int? = null,
    val settled: Boolean = false,
    /** The repayment that settled it, when there was one. */
    val settledByTxnId: String? = null,
    val ownerUid: String,
)

/** Money lent or borrowed outside a bill: "Rohan owes me ₹200", "I owe Priya ₹50". */
@Entity(tableName = "ious", indices = [Index("friendId")])
data class IouEntity(
    @PrimaryKey val id: String,
    val friendId: String,
    val amountPaise: Long,
    /** True when the friend owes the user. */
    val theyOwe: Boolean,
    val note: String? = null,
    val timestamp: Long,
    val settled: Boolean = false,
    val settledByTxnId: String? = null,
    val ownerUid: String,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val syncState: SyncState = SyncState.DIRTY,
)

/**
 * A split another Inkless user made with this user in it, as read from `sharedSplits`. A local
 * copy only, so it shows offline; it comes from the shared document, not the backup.
 */
@Entity(tableName = "incoming_splits")
data class IncomingSplitEntity(
    @PrimaryKey val id: String,
    val payerUid: String,
    val payerName: String,
    val totalPaise: Long,
    val mySharePaise: Long,
    val settled: Boolean,
    val note: String?,
    val createdAt: Long,
)
