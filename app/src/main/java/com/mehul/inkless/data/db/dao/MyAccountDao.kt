package com.mehul.inkless.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.mehul.inkless.data.db.entity.MyAccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MyAccountDao {

    @Query("SELECT * FROM my_accounts WHERE deleted = 0 ORDER BY role, value")
    fun observeAll(): Flow<List<MyAccountEntity>>

    @Query("SELECT * FROM my_accounts WHERE deleted = 0")
    suspend fun getAll(): List<MyAccountEntity>

    @Insert
    suspend fun insert(account: MyAccountEntity)

    @Update
    suspend fun update(account: MyAccountEntity)
}
