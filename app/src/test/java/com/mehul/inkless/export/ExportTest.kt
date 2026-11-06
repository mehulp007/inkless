package com.mehul.inkless.export

import com.google.common.truth.Truth.assertThat
import com.mehul.inkless.data.db.entity.CaptureSource
import com.mehul.inkless.data.db.entity.Direction
import com.mehul.inkless.data.db.entity.SyncState
import com.mehul.inkless.data.db.entity.TransactionEntity
import com.mehul.inkless.data.db.entity.TransactionItemEntity
import com.mehul.inkless.data.db.entity.TransactionStatus
import com.mehul.inkless.data.db.entity.TransactionType
import com.mehul.inkless.sync.TransactionDoc
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class ExportTest {

    // Few rounds keep the test quick; the app uses 210,000.
    private val rounds = 1_000

    @Test
    fun `a locked backup opens with its password and nothing else`() {
        val plain = """{"format":"inkless-backup"}""".toByteArray()
        val locked = BackupCrypto.encrypt(plain, "correct horse".toCharArray(), rounds)
        assertThat(BackupCrypto.isEncrypted(locked)).isTrue()
        assertThat(String(locked)).doesNotContain("inkless-backup")
        assertThat(BackupCrypto.decrypt(locked, "correct horse".toCharArray())).isEqualTo(plain)
        assertThrows(WrongPassword::class.java) { BackupCrypto.decrypt(locked, "wrong horse".toCharArray()) }
    }

    @Test
    fun `a changed byte is caught, and every file is locked differently`() {
        val plain = "same contents".toByteArray()
        val one = BackupCrypto.encrypt(plain, "pw12345678".toCharArray(), rounds)
        val two = BackupCrypto.encrypt(plain, "pw12345678".toCharArray(), rounds)
        assertThat(one).isNotEqualTo(two)
        one[one.size - 3] = (one[one.size - 3] + 1).toByte()
        assertThrows(WrongPassword::class.java) { BackupCrypto.decrypt(one, "pw12345678".toCharArray()) }
    }

    private val payment = TransactionEntity(
        id = "t1", amountPaise = 500_000, direction = Direction.DEBIT, type = TransactionType.EXPENSE,
        timestamp = LocalDateTime.of(2026, 11, 3, 18, 5).toInstant(ZoneOffset.UTC).toEpochMilli(),
        vpa = "amazonupi@apl", payeeRaw = "Amazon", merchantId = "m1", categoryId = "shopping", confidence = 0.85f,
        status = TransactionStatus.CONFIRMED, sources = setOf(CaptureSource.SMS, CaptureSource.NOTIFICATION),
        note = "shoes, \"running\"", tags = listOf("allowance"), createdAt = 1, updatedAt = 2, ownerUid = "u1",
        syncState = SyncState.SYNCED,
    )
    private val items = listOf(
        TransactionItemEntity("i1", "t1", 300_000, "shopping", "Shoes", 0, "u1"),
        TransactionItemEntity("i2", "t1", 200_000, "college", null, 1, "u1"),
    )

    @Test
    fun `the backup file holds the same documents as the cloud backup`() {
        val doc = TransactionDoc(payment, items)
        val text = BackupFile.write(mapOf("transactions" to listOf(BackupRow("t1", doc.toMap()))), exportedAt = 9)
        val back = BackupFile.read(text).getValue("transactions").single()
        val restored = TransactionDoc.fromMap(back.id, back.data, "u1")!!
        assertThat(restored.transaction).isEqualTo(payment)
        assertThat(restored.items).isEqualTo(items)
    }

    @Test
    fun `files that aren't backups are turned away`() {
        assertThrows(BadBackupFile::class.java) { BackupFile.read("not json at all") }
        assertThrows(BadBackupFile::class.java) { BackupFile.read("""{"format":"something-else"}""") }
        val future = """{"format":"inkless-backup","version":99,"tables":{}}"""
        assertThat(assertThrows(BadBackupFile::class.java) { BackupFile.read(future) }.message).contains("newer version")
    }

    @Test
    fun `the spreadsheet has one row per payment, quoted where it needs to be`() {
        val csv = CsvExport.write(
            payments = listOf(payment, payment.copy(id = "gone", deleted = true)),
            items = mapOf("t1" to items),
            friendsShares = mapOf("t1" to 100_000L),
            categoryNames = mapOf("shopping" to "Shopping", "college" to "College & Stationery"),
            merchantNames = mapOf("m1" to "Amazon"),
            zone = ZoneOffset.UTC,
        ).lines().filter { it.isNotEmpty() }
        assertThat(csv).hasSize(2)
        assertThat(csv[0]).startsWith("Date,Time,Amount")
        assertThat(csv[1]).isEqualTo(
            "2026-11-03,18:05,5000.00,Paid,Spending,Shopping,Amazon,Amazon,amazonupi@apl,4000.00," +
                "\"shoes, \"\"running\"\"\",Shoes 3000.00 Shopping | 2000.00 College & Stationery",
        )
    }

    @Test
    fun `text that a spreadsheet would run stays text`() {
        assertThat(CsvExport.cell("=HYPERLINK(\"x\")")).isEqualTo("\"'=HYPERLINK(\"\"x\"\")\"")
        assertThat(CsvExport.cell("+91 98765")).isEqualTo("'+91 98765")
        assertThat(CsvExport.cell("Tapri")).isEqualTo("Tapri")
        assertThat(CsvExport.rupees(5)).isEqualTo("0.05")
    }
}
