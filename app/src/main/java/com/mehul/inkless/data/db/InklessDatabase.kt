package com.mehul.inkless.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.mehul.inkless.data.db.dao.CategoryDao
import com.mehul.inkless.data.db.dao.MerchantDao
import com.mehul.inkless.data.db.dao.MyAccountDao
import com.mehul.inkless.data.db.dao.RawCaptureDao
import com.mehul.inkless.data.db.dao.RuleDao
import com.mehul.inkless.data.db.dao.SyncDao
import com.mehul.inkless.data.db.dao.TransactionDao
import com.mehul.inkless.data.db.dao.TransactionItemDao
import com.mehul.inkless.data.db.entity.CategoryEntity
import com.mehul.inkless.data.db.entity.MerchantEntity
import com.mehul.inkless.data.db.entity.MyAccountEntity
import com.mehul.inkless.data.db.entity.RawCaptureEntity
import com.mehul.inkless.data.db.entity.RuleEntity
import com.mehul.inkless.data.db.entity.TransactionEntity
import com.mehul.inkless.data.db.entity.TransactionItemEntity

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        MerchantEntity::class,
        RawCaptureEntity::class,
        RuleEntity::class,
        MyAccountEntity::class,
        TransactionItemEntity::class,
    ],
    version = 4,
    exportSchema = true,
    // Every schema change from here on ships with a migration and a test in MigrationTest.
    autoMigrations = [
        AutoMigration(from = 1, to = 2), // adds rules
        AutoMigration(from = 2, to = 3), // adds my_accounts
        AutoMigration(from = 3, to = 4), // adds transaction_items
    ],
)
@TypeConverters(Converters::class)
abstract class InklessDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun merchantDao(): MerchantDao
    abstract fun rawCaptureDao(): RawCaptureDao
    abstract fun ruleDao(): RuleDao
    abstract fun myAccountDao(): MyAccountDao
    abstract fun syncDao(): SyncDao
    abstract fun transactionItemDao(): TransactionItemDao
}
