package com.mehul.inkless.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mehul.inkless.data.db.entity.RawCaptureEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RawCaptureDao {

    /** Returns -1 when a capture with the same hash is already stored (the same SMS seen twice). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(capture: RawCaptureEntity): Long

    @Query("SELECT * FROM raw_captures WHERE status = 'UNPARSED' ORDER BY receivedAt DESC")
    fun observeUnparsed(): Flow<List<RawCaptureEntity>>

    @Query("SELECT COUNT(*) FROM raw_captures WHERE status = 'UNPARSED'")
    fun observeUnparsedCount(): Flow<Int>

    @Query("SELECT MAX(receivedAt) FROM raw_captures WHERE source = 'SMS'")
    suspend fun latestSmsTime(): Long?

    @Query("DELETE FROM raw_captures WHERE id = :id")
    suspend fun delete(id: String)
}
