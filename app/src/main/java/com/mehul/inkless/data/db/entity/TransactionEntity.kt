package com.mehul.inkless.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [Index("timestamp"), Index("categoryId"), Index("merchantId"), Index("upiRef")],
)
data class TransactionEntity(
    // Generated on the device and reused as the Firestore document id.
    @PrimaryKey val id: String,
    val amountPaise: Long,
    val direction: Direction,
    val type: TransactionType,
    val timestamp: Long,
    val vpa: String? = null,
    val payeeRaw: String? = null,
    val merchantId: String? = null,
    val accountLast4: String? = null,
    val bankCode: String? = null,
    val upiRef: String? = null,
    val balanceAfterPaise: Long? = null,
    val categoryId: String? = null,
    val confidence: Float = 1f,
    val status: TransactionStatus,
    val sources: Set<CaptureSource>,
    val note: String? = null,
    val tags: List<String> = emptyList(),
    val receiptUri: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val ownerUid: String,
    val deleted: Boolean = false,
    val syncState: SyncState = SyncState.DIRTY,
)
