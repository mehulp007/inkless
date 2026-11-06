package com.mehul.inkless.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mehul.inkless.categorize.CategoryRule
import com.mehul.inkless.categorize.RuleField
import com.mehul.inkless.categorize.RuleMatch

/** A user's categorising rule. Synced like everything else the user creates. */
@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey val id: String,
    val field: RuleField,
    val match: RuleMatch,
    val pattern: String,
    val categoryId: String,
    /** Lower runs first. */
    val priority: Int,
    val ownerUid: String,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val syncState: SyncState = SyncState.DIRTY,
) {
    fun toRule() = CategoryRule(field, match, pattern, categoryId, priority)
}
