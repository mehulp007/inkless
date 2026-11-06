package com.mehul.inkless.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One part of a broken-down payment: "₹1,000 of that Amazon order was a shirt". A payment's items
 * always add up to its amount exactly. They sync inside their payment's cloud document, so they
 * have no sync state of their own: changing them marks the payment as changed instead.
 */
@Entity(tableName = "transaction_items", indices = [Index("transactionId"), Index("categoryId")])
data class TransactionItemEntity(
    @PrimaryKey val id: String,
    val transactionId: String,
    val amountPaise: Long,
    val categoryId: String,
    val note: String? = null,
    /** Order the user added them in; ties for "biggest item" go to the lower one. */
    val sortOrder: Int,
    val ownerUid: String,
)
