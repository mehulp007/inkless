package com.mehul.inkless.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.mehul.inkless.data.db.entity.RuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleDao {

    @Query("SELECT * FROM rules WHERE deleted = 0 ORDER BY priority")
    fun observeAll(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM rules WHERE deleted = 0 ORDER BY priority")
    suspend fun getAll(): List<RuleEntity>

    @Query("SELECT COALESCE(MAX(priority), 0) FROM rules WHERE deleted = 0")
    suspend fun maxPriority(): Int

    @Insert
    suspend fun insert(rule: RuleEntity)

    @Update
    suspend fun update(rules: List<RuleEntity>)
}
