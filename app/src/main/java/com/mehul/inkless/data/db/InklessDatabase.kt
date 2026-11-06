package com.mehul.inkless.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.mehul.inkless.data.db.dao.BudgetDao
import com.mehul.inkless.data.db.dao.CategoryDao
import com.mehul.inkless.data.db.dao.FriendDao
import com.mehul.inkless.data.db.dao.InsightDao
import com.mehul.inkless.data.db.dao.MerchantDao
import com.mehul.inkless.data.db.dao.MyAccountDao
import com.mehul.inkless.data.db.dao.RawCaptureDao
import com.mehul.inkless.data.db.dao.RuleDao
import com.mehul.inkless.data.db.dao.SpendDao
import com.mehul.inkless.data.db.dao.SyncDao
import com.mehul.inkless.data.db.dao.TransactionDao
import com.mehul.inkless.data.db.dao.TransactionItemDao
import com.mehul.inkless.data.db.entity.BudgetEntity
import com.mehul.inkless.data.db.entity.CategoryEntity
import com.mehul.inkless.data.db.entity.FriendEntity
import com.mehul.inkless.data.db.entity.IncomingSplitEntity
import com.mehul.inkless.data.db.entity.IouEntity
import com.mehul.inkless.data.db.entity.ModelCountEntity
import com.mehul.inkless.data.db.entity.MerchantEntity
import com.mehul.inkless.data.db.entity.MyAccountEntity
import com.mehul.inkless.data.db.entity.PredictionEntity
import com.mehul.inkless.data.db.entity.RawCaptureEntity
import com.mehul.inkless.data.db.entity.RecurringEntity
import com.mehul.inkless.data.db.entity.RuleEntity
import com.mehul.inkless.data.db.entity.SplitEntity
import com.mehul.inkless.data.db.entity.SplitShareEntity
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
        BudgetEntity::class,
        FriendEntity::class,
        SplitEntity::class,
        SplitShareEntity::class,
        IouEntity::class,
        IncomingSplitEntity::class,
        ModelCountEntity::class,
        PredictionEntity::class,
        RecurringEntity::class,
    ],
    views = [SpendPart::class],
    version = 7,
    exportSchema = true,
    // Every schema change from here on ships with a migration and a test in MigrationTest.
    autoMigrations = [
        AutoMigration(from = 1, to = 2), // adds rules
        AutoMigration(from = 2, to = 3), // adds my_accounts
        AutoMigration(from = 3, to = 4), // adds transaction_items
        AutoMigration(from = 4, to = 5), // adds budgets and the spend_parts view
        AutoMigration(from = 5, to = 6), // adds friends, splits, IOUs; spend_parts counts only the user's share of a split
        AutoMigration(from = 6, to = 7), // adds the category model's counts, its guesses, and recurring payments
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
    abstract fun budgetDao(): BudgetDao
    abstract fun spendDao(): SpendDao
    abstract fun friendDao(): FriendDao
    abstract fun insightDao(): InsightDao
}
