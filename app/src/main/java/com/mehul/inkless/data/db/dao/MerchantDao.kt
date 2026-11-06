package com.mehul.inkless.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mehul.inkless.data.db.entity.MerchantEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MerchantDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(merchants: List<MerchantEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(merchant: MerchantEntity): Long

    @Query("SELECT * FROM merchants WHERE vpa = :vpa LIMIT 1")
    suspend fun findByVpa(vpa: String): MerchantEntity?

    @Query("UPDATE merchants SET timesSeen = timesSeen + 1, updatedAt = :now, syncState = 'DIRTY' WHERE id = :id")
    suspend fun markSeen(id: String, now: Long)

    @Query("SELECT * FROM merchants WHERE id = :id")
    suspend fun get(id: String): MerchantEntity?

    @Query("SELECT * FROM merchants WHERE id = :id")
    fun observe(id: String): Flow<MerchantEntity?>

    /** Merchant memory: what this merchant's payments should be filed under from now on. */
    @Query("UPDATE merchants SET defaultCategoryId = :categoryId, updatedAt = :now, syncState = 'DIRTY' WHERE id = :id")
    suspend fun setDefaultCategory(id: String, categoryId: String, now: Long)

    @Query("UPDATE merchants SET displayName = :name, updatedAt = :now, syncState = 'DIRTY' WHERE id = :id")
    suspend fun rename(id: String, name: String, now: Long)

    @Query("DELETE FROM merchants")
    suspend fun deleteAll()
}
