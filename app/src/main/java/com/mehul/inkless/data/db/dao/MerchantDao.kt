package com.mehul.inkless.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mehul.inkless.data.db.entity.MerchantEntity

@Dao
interface MerchantDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(merchants: List<MerchantEntity>)

    @Query("DELETE FROM merchants")
    suspend fun deleteAll()
}
