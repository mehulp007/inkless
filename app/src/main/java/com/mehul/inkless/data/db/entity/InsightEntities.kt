package com.mehul.inkless.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The category model's memory: how often a feature (a word, a piece of one, an amount range, a
 * time of day) was seen in payments of a category. Rebuilt from the payments themselves, so it
 * stays on the phone and isn't backed up.
 */
@Entity(tableName = "model_counts", primaryKeys = ["feature", "categoryId"])
data class ModelCountEntity(
    val feature: String,
    val categoryId: String,
    val count: Int,
)

/** Who filed a payment when it arrived. */
enum class PredictionSource { RULE, MERCHANT_MEMORY, MODEL, KEYWORD, NONE }

/**
 * What Inkless thought of a payment when it arrived: who filed it (if anyone) and the model's own
 * guess. Compared with the category the payment ends up with, this is the accuracy chart.
 */
@Entity(tableName = "predictions")
data class PredictionEntity(
    @PrimaryKey val transactionId: String,
    val source: PredictionSource,
    /** The category it was filed under automatically; null when it was left for the user. */
    val filedCategoryId: String?,
    val modelCategoryId: String?,
    val modelConfidence: Float?,
    val createdAt: Long,
)

/** A payment that comes round regularly: rent-like, weekly laundry, a monthly subscription. */
@Entity(tableName = "recurring")
data class RecurringEntity(
    @PrimaryKey val merchantId: String,
    val name: String,
    val categoryId: String?,
    val typicalAmountPaise: Long,
    val intervalDays: Int,
    val occurrences: Int,
    val lastPaidAt: Long,
    val nextExpectedAt: Long,
)
