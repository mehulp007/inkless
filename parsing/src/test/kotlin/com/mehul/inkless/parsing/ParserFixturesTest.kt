package com.mehul.inkless.parsing

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Runs every message in src/test/resources/parser-fixtures/ through [SmsReader] and checks the
 * result against the "expect:" line written above it.
 */
@RunWith(Parameterized::class)
class ParserFixturesTest(private val case: Fixture) {

    @Test
    fun readsAsExpected() {
        val result = if (case.notification) {
            NotificationReader.read(case.sender, case.body)
        } else {
            SmsReader(BankParsers.all).read(case.sender, case.body)
        }
        val label = "${case.file}: ${case.body.take(60)}…"
        val expect = case.expect

        when {
            expect.startsWith("REJECT ") -> {
                val reason = Rejection.valueOf(expect.removePrefix("REJECT ").trim())
                assertWithMessage(label).that(result).isEqualTo(SmsResult.Rejected(reason))
            }
            expect == "UNPARSED" -> assertWithMessage(label).that(result).isEqualTo(SmsResult.Unparsed)
            else -> {
                assertWithMessage(label).that(result).isInstanceOf(SmsResult.Parsed::class.java)
                checkParsed(label, (result as SmsResult.Parsed).txn, expect)
            }
        }
    }

    private fun checkParsed(label: String, txn: ParsedTxn, expect: String) {
        val head = expect.substringBefore(" payee=")
        val payee = expect.substringAfter(" payee=", missingDelimiterValue = "").ifEmpty { null }
        val parts = head.split(" ").filter { it.isNotBlank() }

        assertWithMessage("$label direction").that(txn.direction).isEqualTo(TxnDirection.valueOf(parts[0]))
        assertWithMessage("$label amount").that(txn.amountPaise).isEqualTo(Amounts.toPaise(parts[1]))

        for (part in parts.drop(2)) {
            val (key, value) = part.split("=", limit = 2)
            val actual: Any? = when (key) {
                "vpa" -> txn.vpa
                "ref" -> txn.upiRef
                "acct" -> txn.accountLast4
                "bal" -> txn.balancePaise
                "refund" -> txn.isRefund
                else -> error("unknown key $key in ${case.file}")
            }
            val expected: Any? = when (key) {
                "bal" -> Amounts.toPaise(value)
                "refund" -> value.toBoolean()
                else -> value
            }
            assertWithMessage("$label $key").that(actual).isEqualTo(expected)
        }
        if (payee != null) assertWithMessage("$label payee").that(txn.payee).isEqualTo(payee)
    }

    /** SMS cases sit in parser-fixtures/, UPI app notification cases in parser-fixtures/notifications/. */
    data class Fixture(
        val file: String,
        val sender: String,
        val expect: String,
        val body: String,
        val notification: Boolean = false,
    ) {
        override fun toString() = "$file / ${body.take(40)}"
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Fixture> {
            val dir = java.io.File(ParserFixturesTest::class.java.getResource("/parser-fixtures")!!.toURI())
            val sms = dir.listFiles { f -> f.extension == "txt" }!!.sortedBy { it.name }.flatMap { read(it, notification = false) }
            val notifications = java.io.File(dir, "notifications").listFiles { f -> f.extension == "txt" }.orEmpty()
                .sortedBy { it.name }.flatMap { read(it, notification = true) }
            return sms + notifications
        }

        private fun read(file: java.io.File, notification: Boolean): List<Fixture> =
            file.readText()
                .replace("\r\n", "\n")
                .split(Regex("""^---\s*$""", RegexOption.MULTILINE))
                .mapNotNull { block ->
                    val lines = block.lines().filterNot { it.startsWith("#") }.dropWhile { it.isBlank() }
                    if (lines.size < 3 || !lines[0].startsWith("sender:")) return@mapNotNull null
                    Fixture(
                        file = file.name,
                        sender = lines[0].removePrefix("sender:").trim(),
                        expect = lines[1].removePrefix("expect:").trim(),
                        body = lines.drop(2).joinToString("\n").trim(),
                        notification = notification,
                    )
                }
    }
}
