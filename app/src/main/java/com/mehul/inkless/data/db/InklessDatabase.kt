package com.mehul.inkless.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.mehul.inkless.data.db.dao.CategoryDao
import com.mehul.inkless.data.db.dao.MerchantDao
import com.mehul.inkless.data.db.dao.RawCaptureDao
import com.mehul.inkless.data.db.dao.TransactionDao
import com.mehul.inkless.data.db.entity.CategoryEntity
import com.mehul.inkless.data.db.entity.MerchantEntity
import com.mehul.inkless.data.db.entity.RawCaptureEntity
import com.mehul.inkless.data.db.entity.TransactionEntity

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        MerchantEntity::class,
        RawCaptureEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class InklessDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun merchantDao(): MerchantDao
    abstract fun rawCaptureDao(): RawCaptureDao
}
