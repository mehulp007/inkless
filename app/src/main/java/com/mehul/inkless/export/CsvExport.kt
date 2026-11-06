package com.mehul.inkless.export

import com.mehul.inkless.data.db.entity.Direction
import com.mehul.inkless.data.db.entity.TransactionEntity
import com.mehul.inkless.data.db.entity.TransactionItemEntity
import com.mehul.inkless.data.db.entity.TransactionType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Payments as a spreadsheet, one row each, for Excel or Google Sheets. Amounts are plain numbers
 * in rupees ("450.00") so a spreadsheet can add them up. Split payments get "Your part" too.
 */
object CsvExport {

    val HEADER = listOf("Date", "Time", "Amount", "Direction", "Type", "Category", "Place", "Payee", "UPI ID", "Your part", "Note", "Items")

    private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val TIME = DateTimeFormatter.ofPattern("HH:mm")

    fun write(
        payments: List<TransactionEntity>,
        items: Map<String, List<TransactionItemEntity>>,
        friendsShares: Map<String, Long>,
        categoryNames: Map<String, String>,
        merchantNames: Map<String, String>,
        zone: ZoneId,
    ): String = buildString {
        appendLine(HEADER.joinToString(",") { cell(it) })
        for (t in payments.filter { !it.deleted }.sortedBy { it.timestamp }) {
            val time = Instant.ofEpochMilli(t.timestamp).atZone(zone)
            val shares = friendsShares[t.id]
            val row = listOf(
                time.format(DATE),
                time.format(TIME),
                rupees(t.amountPaise),
                if (t.direction == Direction.DEBIT) "Paid" else "Received",
                typeName(t.type),
                t.categoryId?.let { categoryNames[it] ?: it }.orEmpty(),
                t.merchantId?.let(merchantNames::get).orEmpty(),
                t.payeeRaw.orEmpty(),
                t.vpa.orEmpty(),
                shares?.let { rupees(t.amountPaise - it) }.orEmpty(),
                t.note.orEmpty(),
                items[t.id].orEmpty().sortedBy { it.sortOrder }.joinToString(" | ") { item ->
                    listOfNotNull(item.note, rupees(item.amountPaise), categoryNames[item.categoryId] ?: item.categoryId).joinToString(" ")
                },
            )
            appendLine(row.joinToString(",") { cell(it) })
        }
    }

    fun rupees(paise: Long): String = "${paise / 100}.${(paise % 100).toString().padStart(2, '0')}"

    private fun typeName(type: TransactionType) = when (type) {
        TransactionType.EXPENSE -> "Spending"
        TransactionType.INCOME -> "Money in"
        TransactionType.REFUND -> "Refund"
        TransactionType.SELF_TRANSFER -> "Own transfer"
        TransactionType.SPLIT_SETTLEMENT -> "Paid back by a friend"
        TransactionType.CC_BILL -> "Card bill"
    }

    /**
     * Quotes a cell when it has to, and defuses text a spreadsheet would run as a formula
     * (a payee named "=HYPERLINK(…)" must stay text).
     */
    fun cell(raw: String): String {
        val safe = if (raw.isNotEmpty() && raw[0] in "=+-@\t\r") "'$raw" else raw
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }
}
