package com.mehul.inkless.parsing

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/** Runs every case in `order-fixtures/orders.txt` through [OrderText]. */
class OrderTextTest {

    private data class Case(val total: Long?, val items: List<OrderLine>, val text: String)

    private fun cases(): List<Case> {
        val raw = checkNotNull(javaClass.classLoader.getResource("order-fixtures/orders.txt")).readText()
        return raw.split(Regex("""(?m)^---\s*$"""))
            .map { it.trim() }
            .filter { block -> block.lines().any { it.startsWith("total:") } }
            .map { block ->
                val (head, text) = block.split(Regex("""(?m)^text:\s*$"""), limit = 2)
                val headLines = head.lines().map { it.trim() }
                val total = headLines.first { it.startsWith("total:") }.substringAfter(':').trim()
                Case(
                    total = if (total == "none") null else Amounts.toPaise(total),
                    items = headLines.filter { it.startsWith("item:") }.map { line ->
                        val (name, rupees) = line.substringAfter(':').split('|').map { it.trim() }
                        OrderLine(name, checkNotNull(Amounts.toPaise(rupees)))
                    },
                    text = text.trim(),
                )
            }
    }

    @Test
    fun everyFixtureParses() {
        val cases = cases()
        assertWithMessage("fixtures found").that(cases.size).isAtLeast(7)
        for (case in cases) {
            val parsed = OrderText.parse(case.text)
            val name = case.text.lines().first()
            assertWithMessage("items of: $name").that(parsed.items).isEqualTo(case.items)
            assertWithMessage("total of: $name").that(parsed.totalPaise).isEqualTo(case.total)
        }
    }
}
