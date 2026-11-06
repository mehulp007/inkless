package com.mehul.inkless.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The original SMS/notification as it arrived. Kept only on this device so a message can be
 * re-parsed later; it is never synced, which is why it has no sync columns.
 */
@Entity(tableName = "raw_captures", indices = [Index(value = ["bodyHash"], unique = true)])
data class RawCaptureEntity(
    @PrimaryKey val id: String,
    val source: CaptureSource,
    val sender: String,
    val bodyHash: String,
    val body: String?,
    val receivedAt: Long,
    val status: RawCaptureStatus,
    val transactionId: String? = null,
)
