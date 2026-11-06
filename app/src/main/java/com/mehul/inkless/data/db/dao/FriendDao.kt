package com.mehul.inkless.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.mehul.inkless.data.db.entity.FriendEntity
import com.mehul.inkless.data.db.entity.IncomingSplitEntity
import com.mehul.inkless.data.db.entity.IouEntity
import com.mehul.inkless.data.db.entity.SplitEntity
import com.mehul.inkless.data.db.entity.SplitShareEntity
import kotlinx.coroutines.flow.Flow

/** A friend's part of a split, with the payment it was part of. */
data class ShareLine(
    val shareId: String,
    val splitId: String,
    val friendId: String,
    val sharePaise: Long,
    val settled: Boolean,
    val transactionId: String,
    val timestamp: Long,
    val title: String?,
)

@Dao
interface FriendDao {

    // Friends

    @Query("SELECT * FROM friends WHERE deleted = 0 ORDER BY name COLLATE NOCASE")
    fun observeFriends(): Flow<List<FriendEntity>>

    @Query("SELECT * FROM friends WHERE deleted = 0 ORDER BY name COLLATE NOCASE")
    suspend fun friends(): List<FriendEntity>

    @Query("SELECT * FROM friends WHERE id = :id")
    fun observeFriend(id: String): Flow<FriendEntity?>

    @Query("SELECT * FROM friends WHERE id = :id")
    suspend fun friend(id: String): FriendEntity?

    @Upsert
    suspend fun saveFriend(friend: FriendEntity)

    // Splits

    @Query("SELECT * FROM splits WHERE transactionId = :transactionId AND deleted = 0")
    fun observeSplitFor(transactionId: String): Flow<SplitEntity?>

    @Query("SELECT * FROM splits WHERE transactionId = :transactionId")
    suspend fun splitFor(transactionId: String): SplitEntity?

    @Query("SELECT * FROM splits WHERE id = :id")
    suspend fun split(id: String): SplitEntity?

    @Query("SELECT * FROM splits WHERE deleted = 0")
    suspend fun liveSplits(): List<SplitEntity>

    @Query("SELECT * FROM splits WHERE deleted = 1 AND sharedSplitId IS NOT NULL")
    suspend fun deletedSharedSplits(): List<SplitEntity>

    @Upsert
    suspend fun saveSplit(split: SplitEntity)

    @Query("SELECT * FROM split_shares WHERE splitId = :splitId")
    fun observeShares(splitId: String): Flow<List<SplitShareEntity>>

    @Query("SELECT * FROM split_shares WHERE splitId = :splitId")
    suspend fun shares(splitId: String): List<SplitShareEntity>

    @Query("SELECT * FROM split_shares WHERE id = :id")
    suspend fun share(id: String): SplitShareEntity?

    @Insert
    suspend fun insertShares(shares: List<SplitShareEntity>)

    @Query("DELETE FROM split_shares WHERE splitId = :splitId")
    suspend fun deleteShares(splitId: String)

    @Transaction
    suspend fun replaceShares(splitId: String, shares: List<SplitShareEntity>) {
        deleteShares(splitId)
        if (shares.isNotEmpty()) insertShares(shares)
    }

    @Query("UPDATE split_shares SET settled = :settled, settledByTxnId = :byTxnId WHERE id = :id")
    suspend fun setShareSettled(id: String, settled: Boolean, byTxnId: String?)

    @Query(
        """
        SELECT s.id AS shareId, s.splitId, s.friendId, s.sharePaise, s.settled, sp.transactionId, t.timestamp,
               COALESCE(m.displayName, t.payeeRaw, t.vpa) AS title
        FROM split_shares s
        JOIN splits sp ON sp.id = s.splitId AND sp.deleted = 0
        JOIN transactions t ON t.id = sp.transactionId AND t.deleted = 0
        LEFT JOIN merchants m ON m.id = t.merchantId
        ORDER BY t.timestamp DESC
        """,
    )
    fun observeShareLines(): Flow<List<ShareLine>>

    @Query(
        """
        SELECT s.id AS shareId, s.splitId, s.friendId, s.sharePaise, s.settled, sp.transactionId, t.timestamp,
               COALESCE(m.displayName, t.payeeRaw, t.vpa) AS title
        FROM split_shares s
        JOIN splits sp ON sp.id = s.splitId AND sp.deleted = 0
        JOIN transactions t ON t.id = sp.transactionId AND t.deleted = 0
        LEFT JOIN merchants m ON m.id = t.merchantId
        WHERE s.friendId = :friendId AND s.settled = 0
        ORDER BY t.timestamp
        """,
    )
    suspend fun openShares(friendId: String): List<ShareLine>

    // IOUs

    @Query("SELECT * FROM ious WHERE deleted = 0 ORDER BY timestamp DESC")
    fun observeIous(): Flow<List<IouEntity>>

    @Query("SELECT * FROM ious WHERE friendId = :friendId AND deleted = 0 AND settled = 0 ORDER BY timestamp")
    suspend fun openIous(friendId: String): List<IouEntity>

    @Query("SELECT * FROM ious WHERE id = :id")
    suspend fun iou(id: String): IouEntity?

    @Upsert
    suspend fun saveIou(iou: IouEntity)

    // Splits friends shared with this user

    @Query("SELECT * FROM incoming_splits ORDER BY createdAt DESC")
    fun observeIncoming(): Flow<List<IncomingSplitEntity>>

    @Query("SELECT * FROM incoming_splits WHERE id = :id")
    suspend fun incoming(id: String): IncomingSplitEntity?

    @Query("DELETE FROM incoming_splits")
    suspend fun clearIncoming()

    @Insert
    suspend fun insertIncoming(splits: List<IncomingSplitEntity>)

    @Transaction
    suspend fun replaceIncoming(splits: List<IncomingSplitEntity>) {
        clearIncoming()
        if (splits.isNotEmpty()) insertIncoming(splits)
    }

    @Query("UPDATE incoming_splits SET settled = :settled WHERE id = :id")
    suspend fun setIncomingSettled(id: String, settled: Boolean)
}
