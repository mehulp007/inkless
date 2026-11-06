package com.mehul.inkless.sync

import com.google.common.truth.Truth.assertThat
import com.mehul.inkless.data.db.entity.CaptureSource
import com.mehul.inkless.data.db.entity.Direction
import com.mehul.inkless.data.db.entity.SyncState
import com.mehul.inkless.data.db.entity.TransactionEntity
import com.mehul.inkless.data.db.entity.TransactionStatus
import com.mehul.inkless.data.db.entity.TransactionType
import org.junit.Test

class LastWriteWinsTest {

    private fun txn(id: String, updatedAt: Long, note: String? = null, receipt: String? = null, state: SyncState = SyncState.SYNCED) =
        TransactionEntity(
            id = id, amountPaise = 4500, direction = Direction.DEBIT, type = TransactionType.EXPENSE, timestamp = 1,
            status = TransactionStatus.AUTO, sources = setOf(CaptureSource.SMS), note = note, receiptUri = receipt,
            createdAt = 1, updatedAt = updatedAt, ownerUid = "u1", syncState = state,
        )

    private val table = SyncTables.transactions

    @Test
    fun `a row the phone doesn't have yet is taken`() {
        assertThat(table.winners(listOf(txn("t1", 5)), emptyList())).containsExactly(txn("t1", 5))
    }

    @Test
    fun `the later edit wins either way`() {
        val cloudNewer = table.winners(listOf(txn("t1", 9, note = "cloud")), listOf(txn("t1", 5, note = "phone", state = SyncState.DIRTY)))
        val phoneNewer = table.winners(listOf(txn("t1", 5, note = "cloud")), listOf(txn("t1", 9, note = "phone", state = SyncState.DIRTY)))
        assertThat(cloudNewer.single().note).isEqualTo("cloud")
        assertThat(phoneNewer).isEmpty()
    }

    @Test
    fun `the phone's own upload coming back changes nothing`() {
        assertThat(table.winners(listOf(txn("t1", 5)), listOf(txn("t1", 5)))).isEmpty()
    }

    @Test
    fun `a receipt photo on this phone survives a newer cloud copy`() {
        val won = table.winners(listOf(txn("t1", 9, note = "cloud")), listOf(txn("t1", 5, receipt = "content://receipt/1")))
        assertThat(won.single().receiptUri).isEqualTo("content://receipt/1")
        assertThat(won.single().note).isEqualTo("cloud")
    }

    @Test
    fun `categories and merchants arrive before the transactions that point at them`() {
        assertThat(SyncTables.collections).containsExactly("categories", "merchants", "rules", "myAccounts", "transactions").inOrder()
    }
}
