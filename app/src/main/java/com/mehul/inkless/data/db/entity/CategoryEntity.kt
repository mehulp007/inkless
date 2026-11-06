package com.mehul.inkless.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val emoji: String,
    val colorArgb: Long,
    val monthlyBudgetPaise: Long? = null,
    val sortOrder: Int,
    val isSystem: Boolean,
    val ownerUid: String,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val syncState: SyncState = SyncState.DIRTY,
)
