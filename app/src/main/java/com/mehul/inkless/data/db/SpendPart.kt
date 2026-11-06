package com.mehul.inkless.data.db

import androidx.room.DatabaseView

/**
 * Spending, one row per category a payment counts in: a broken-down payment gives one row per
 * item, any other payment one row. A payment split with friends counts only the user's share.
 * Refunds are negative, so they take back from their category.
 * Every spend number in the app (category totals, budgets, safe-to-spend, the widget, the weekly
 * summary) is worked out from this view, so they all agree on how items and refunds count.
 */
@DatabaseView(
    viewName = "spend_parts",
    value = """
        SELECT t.id AS transactionId, t.timestamp AS timestamp, t.merchantId AS merchantId,
               COALESCE(i.categoryId, t.categoryId) AS categoryId,
               CASE WHEN t.direction = 'DEBIT'
                    THEN COALESCE(i.amountPaise, t.amountPaise - COALESCE(
                        (SELECT SUM(s.sharePaise) FROM split_shares s JOIN splits sp ON sp.id = s.splitId
                         WHERE sp.transactionId = t.id AND sp.deleted = 0), 0))
                    ELSE -COALESCE(i.amountPaise, t.amountPaise) END AS amountPaise
        FROM transactions t
        LEFT JOIN transaction_items i ON i.transactionId = t.id
        WHERE t.deleted = 0 AND t.type IN ('EXPENSE', 'REFUND')
    """,
)
data class SpendPart(
    val transactionId: String,
    val timestamp: Long,
    val merchantId: String?,
    val categoryId: String?,
    /** Negative for refunds. */
    val amountPaise: Long,
)
