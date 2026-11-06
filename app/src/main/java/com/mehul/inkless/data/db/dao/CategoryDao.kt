package com.mehul.inkless.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mehul.inkless.data.db.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories WHERE deleted = 0 ORDER BY sortOrder")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE deleted = 0 ORDER BY sortOrder")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    fun observe(id: String): Flow<CategoryEntity?>

    /** Null clears the budget. */
    @Query("UPDATE categories SET monthlyBudgetPaise = :paise, updatedAt = :now, syncState = 'DIRTY' WHERE id = :id")
    suspend fun setBudget(id: String, paise: Long?, now: Long)

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<CategoryEntity>)
}
