package com.mehul.inkless.parsing

/** One thing bought, as an order page lists it. */
data class OrderLine(val name: String, val amountPaise: Long)

data class ParsedOrder(
    val items: List<OrderLine>,
    /** What was actually paid (after delivery, fees and discounts), when the page says. */
    val totalPaise: Long?,
)

/**
 * Reads the items and the total out of an order someone shared: text read off a screenshot of an
 * Amazon/Flipkart/Blinkit order page, or a plain list typed out ("Shirt 1000"). Order pages put
 * each item's name on one line and its price on the same or the next line, then a bill with
 * subtotals, fees and the total at the end.
 */
object OrderText {

    private val ignoreCase = setOf(RegexOption.IGNORE_CASE)

    // Text read off a screenshot has no "₹" (the English OCR model doesn't know it): it comes out
    // as "%", "=" or a "7" stuck to the number. The first two count as rupee signs here; the "7"
    // is worked out from the bill (see [parse]).
    private val price = Regex("""(?:₹|%|=|\brs\.?|\binr)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", ignoreCase)
    private val bareNumber = Regex("""^[0-9][0-9,]+(?:\.[0-9]{1,2})?$""")
    private val itemsSubtotal = Regex("""\b(?:sub ?total|item\(?s?\)? ?total|items? subtotal)\b""", ignoreCase)
    private val currency = Regex("""₹|\brs\.?\s*[0-9]|\binr\b""", ignoreCase)

    // A typed list without rupee signs: "Shirt 1000", "Shoes - 1,500".
    private val plainItem = Regex("""^(.*[a-z].*?)\s*[-:–=]?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)$""", ignoreCase)

    private val total = Regex(
        """\b(?:grand total|order total|amount paid|total paid|to pay|bill total|total amount|net amount|total payable|total)\b""",
        ignoreCase,
    )

    // Bill lines that aren't things bought, and aren't the final total either.
    private val billLine = Regex(
        """\b(?:sub ?total|item\(?s?\)? ?total|items? subtotal|price \(|delivery|shipping|fee|charges?|handling|packaging|""" +
            """platform|convenience|discount|coupon|promotion|promo|offer|savings?|saved|tax|gst|cgst|sgst|tip|donation|""" +
            """cashback|m\.?r\.?p|rounding|round off|wallet|reward)""",
        ignoreCase,
    )

    // Lines around an item that describe it rather than name it.
    private val detailLine = Regex(
        """^(?:qty|quantity|size|colou?r|seller|sold by|delivered|arriving|arrived|order (?:id|number|no|#|placed|details|summary)|""" +
            """return|exchange|rate |rating|ship(?:ped|ment)|payment|bill details|price details|view|track|buy it again|invoice)""",
        ignoreCase,
    )

    // "1 unit", "6 items in this order", "500 ml x 2": how many, not what.
    private val quantityLine = Regex("""^\d+(?:\.\d+)?\s*(?:x\s*\d+\s*)?(?:units?|pcs?|pieces?|items?|nos?|packs?|ml|ltrs?|l|gm?|kg)\b""", ignoreCase)

    private val word = Regex("""[a-z]{3,}""", ignoreCase)
    private val spaces = Regex("""\s+""")

    fun parse(text: String): ParsedOrder {
        val lines = text.lines().map { it.replace(spaces, " ").trim() }.filter { it.isNotEmpty() }
        val typedList = !currency.containsMatchIn(text) && !text.contains('%')

        val items = mutableListOf<Found>()
        var totalPaise: Long? = null
        var subtotalPaise: Long? = null
        var waitingForTotal = false
        var pendingName: String? = null

        for (line in lines) {
            val amounts = price.findAll(line).mapNotNull { Amounts.toPaise(it.groupValues[1]) }.toList()
            val label = price.replace(line, " ").replace(spaces, " ").trim(' ', ':', '-', '–', '=', '.', ',')

            if (amounts.isEmpty()) {
                val name = pendingName
                when {
                    typedList -> plainItem.find(line)?.takeIf { !isNoise(it.groupValues[1]) }?.let { match ->
                        val paise = Amounts.toPaise(match.groupValues[2])
                        if (paise != null && paise > 0) items += Found(cleanName(match.groupValues[1]), paise)
                    }
                    // A price under an item name whose "₹" was lost, maybe read as a leading "7".
                    bareNumber.matches(line) && name != null -> Amounts.toPaise(line)?.let { paise ->
                        val withoutSeven = if (line.startsWith('7')) Amounts.toPaise(line.drop(1).trimStart(',')) else null
                        items += Found(cleanName(name), paise, withoutSeven)
                        pendingName = null
                    }
                    isTotal(line) -> waitingForTotal = true
                    !isNoise(line) -> pendingName = line
                }
                continue
            }

            // A page may print the M.R.P. next to the price: the price is the lower one.
            val amount = amounts.min()
            when {
                waitingForTotal && label.isEmpty() -> {
                    totalPaise = amount
                    waitingForTotal = false
                }
                isTotal(label) -> totalPaise = amount
                itemsSubtotal.containsMatchIn(label) -> subtotalPaise = amount
                billLine.containsMatchIn(label) -> Unit
                label.isNotEmpty() && word.containsMatchIn(label) && !isNoise(label) -> items += Found(cleanName(label), amount)
                label.isEmpty() && pendingName != null -> {
                    items += Found(cleanName(pendingName), amount)
                    pendingName = null
                }
            }
        }

        // A doubtful leading "7" stays a 7 only when that's what makes the items add up to the bill.
        val target = subtotalPaise ?: totalPaise
        val keepSevens = target != null && items.sumOf { it.amountPaise } == target
        val resolved = items.map { OrderLine(it.name, if (keepSevens) it.amountPaise else it.withoutSeven ?: it.amountPaise) }
        return ParsedOrder(resolved.filter { it.amountPaise > 0 }, totalPaise)
    }

    /** An item as found; [withoutSeven] is its price if a leading "7" was really a "₹". */
    private class Found(val name: String, val amountPaise: Long, val withoutSeven: Long? = null)

    private fun isTotal(label: String) = total.containsMatchIn(label) && !billLine.containsMatchIn(label)

    private fun isNoise(line: String) =
        detailLine.containsMatchIn(line) || billLine.containsMatchIn(line) || quantityLine.containsMatchIn(line) || !word.containsMatchIn(line)

    private fun cleanName(name: String) = name.trim(' ', ':', '-', '–', '=', '.', ',').take(MAX_NAME)

    private const val MAX_NAME = 60
}
