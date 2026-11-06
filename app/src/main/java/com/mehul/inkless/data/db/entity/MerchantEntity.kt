package com.mehul.inkless.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "merchants", indices = [Index(value = ["vpa"], unique = true)])
data class MerchantEntity(
    @PrimaryKey val id: String,
    val vpa: String,
    val displayName: String,
    val defaultCategoryId: String? = null,
    val timesSeen: Int = 0,
    val ownerUid: String,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val syncState: SyncState = SyncState.DIRTY,
)
