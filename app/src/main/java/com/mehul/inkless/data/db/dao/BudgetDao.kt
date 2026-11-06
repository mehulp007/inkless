package com.mehul.inkless.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.mehul.inkless.data.db.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {

    @Query("SELECT * FROM budgets WHERE id = :id AND deleted = 0")
    fun observe(id: String = BudgetEntity.MONTHLY_ID): Flow<BudgetEntity?>

    @Query("SELECT * FROM budgets WHERE id = :id AND deleted = 0")
    suspend fun get(id: String = BudgetEntity.MONTHLY_ID): BudgetEntity?

    @Upsert
    suspend fun save(budget: BudgetEntity)
}
