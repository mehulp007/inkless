package com.mehul.inkless.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class BudgetPeriod { MONTH, SEMESTER }

/**
 * The money the user lives on: their monthly allowance and the day it usually arrives, which is
 * where each budget cycle starts. [dailyFoodCapPaise] is what they mean to spend on food outside
 * the mess on a normal day.
 */
@Entity(tableName = "budgets")
data class BudgetEntity(
    /** [MONTHLY_ID] for the allowance, so every device of the account edits the same row. */
    @PrimaryKey val id: String,
    val period: BudgetPeriod,
    val amountPaise: Long,
    /** Day of the month the allowance arrives, 1–31 (a short month uses its last day). */
    val allowanceDay: Int?,
    val dailyFoodCapPaise: Long? = null,
    val ownerUid: String,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val syncState: SyncState = SyncState.DIRTY,
) {
    companion object {
        const val MONTHLY_ID = "monthly"
    }
}
