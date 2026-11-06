package com.mehul.inkless.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** What [MyAccountEntity.value] is. */
enum class AccountMatch {
    /** A UPI ID like "mehul@oksbi". */
    UPI_ID,

    /** A name as banks print it, like "MEHUL PATIL". */
    NAME,
}

enum class AccountRole {
    /** The user's own account: money moving to or from it is a transfer, not spending or income. */
    MINE,

    /** Home / parents: money from them is allowance. */
    HOME,
}

@Entity(tableName = "my_accounts")
data class MyAccountEntity(
    @PrimaryKey val id: String,
    val match: AccountMatch,
    val value: String,
    val role: AccountRole,
    val label: String?,
    val ownerUid: String,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val syncState: SyncState = SyncState.DIRTY,
)
