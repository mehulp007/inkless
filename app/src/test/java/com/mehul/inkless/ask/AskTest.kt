package com.mehul.inkless.ask

import com.google.common.truth.Truth.assertThat
import com.mehul.inkless.data.db.SpendPart
import com.mehul.inkless.data.db.dao.PaymentRow
import com.mehul.inkless.data.db.entity.FriendEntity
import com.mehul.inkless.data.db.entity.IouEntity
import com.mehul.inkless.splits.FriendBalance
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class AskTest {

    private val today = LocalDate.of(2026, 11, 7) // a Saturday
    private val categories = mapOf(
        "food_canteen" to "Food & Canteen",
        "food_delivery" to "Food Delivery",
        "travel" to "Travel",
        "college" to "College & Stationery",
        "laundry" to "Laundry & Hostel",
        "other" to "Other",
    )

    private fun parse(q: String) = AskParser.parse(q, today, categories, listOf("Rohan Mehta", "Priya Nair"))

    @Test
    fun `totals by category and period`() {
        parse("How much did I spend on food this month?").let {
            assertThat(it.measure).isEqualTo(Measure.TOTAL)
            assertThat(it.categoryIds).containsExactly("food_canteen", "food_delivery")
            assertThat(it.span).isEqualTo(DateSpan(LocalDate.of(2026, 11, 1), today, "this month"))
        }
        parse("travel last week").let {
            assertThat(it.categoryIds).containsExactly("travel")
            assertThat(it.span.start).isEqualTo(LocalDate.of(2026, 10, 26))
            assertThat(it.span.end).isEqualTo(LocalDate.of(2026, 11, 1))
        }
        assertThat(parse("how much on autos yesterday").categoryIds).containsExactly("travel")
        assertThat(parse("printouts in october").span.label).isEqualTo("in October")
        assertThat(parse("canteen in december").span.start).isEqualTo(LocalDate.of(2025, 12, 1))
        assertThat(parse("spend in the last 10 days").span.start).isEqualTo(LocalDate.of(2026, 10, 29))
    }

    @Test
    fun `places are what follows on, at, from or to`() {
        parse("How many times did I order from Swiggy?").let {
            assertThat(it.measure).isEqualTo(Measure.COUNT)
            assertThat(it.place).isEqualTo("swiggy")
            assertThat(it.categoryIds).isEmpty()
        }
        assertThat(parse("how much at sharma canteen last month").categoryIds).containsExactly("food_canteen")
        assertThat(parse("how much on zomato this week").place).isEqualTo("zomato")
        assertThat(parse("total spent in october").place).isNull()
    }

    @Test
    fun `other kinds of question`() {
        assertThat(parse("biggest payment last month").measure).isEqualTo(Measure.BIGGEST)
        assertThat(parse("average per day this week").measure).isEqualTo(Measure.DAILY_AVERAGE)
        assertThat(parse("which category did I spend most on").measure).isEqualTo(Measure.TOP_CATEGORIES)
        assertThat(parse("top places this month").measure).isEqualTo(Measure.TOP_PLACES)
        parse("How much does Rohan owe me?").let {
            assertThat(it.measure).isEqualTo(Measure.FRIEND_BALANCE)
            assertThat(it.friend).isEqualTo("Rohan Mehta")
        }
    }

    @Test
    fun `a bit of hinglish`() {
        parse("kitna kharcha kal").let {
            assertThat(it.measure).isEqualTo(Measure.TOTAL)
            assertThat(it.span.label).isEqualTo("yesterday")
        }
        assertThat(parse("pichle mahine khana pe kitna").categoryIds).containsExactly("food_canteen", "food_delivery")
        assertThat(parse("pichle mahine khana pe kitna").span.label).isEqualTo("last month")
        // "kal" is a word on its own, not the start of Kalyan Stores.
        assertThat(parse("how much at kalyan stores").span.label).isEqualTo("this month")
    }

    private fun ms(day: Int, hour: Int = 12) = LocalDate.of(2026, 11, day).atTime(hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private val payments = listOf(
        PaymentRow("a", ms(2), 450_00, "food_delivery", "m1", "Swiggy", "swiggy@axis"),
        PaymentRow("b", ms(3), 40_00, "food_canteen", "m2", "Sharma Canteen", null),
        PaymentRow("c", ms(5), 380_00, "food_delivery", "m1", "Swiggy", "swiggy@axis"),
        PaymentRow("d", ms(6), 120_00, "travel", "m3", "Rapido", null),
    )
    // The ₹450 Swiggy order was split with a friend, so it counts ₹225.
    private val parts = listOf(
        SpendPart("a", ms(2), "m1", "food_delivery", 225_00),
        SpendPart("b", ms(3), "m2", "food_canteen", 40_00),
        SpendPart("c", ms(5), "m1", "food_delivery", 380_00),
        SpendPart("d", ms(6), "m3", "travel", 120_00),
    )

    private fun answer(q: String, balances: List<FriendBalance> = emptyList()) = AskEngine.answer(
        AskParser.parse(q, today, categories, balances.map { it.friend.name }),
        parts,
        payments,
        categories.mapValues { (_, n) -> n },
        balances,
        ZoneOffset.UTC,
    )

    @Test
    fun `answers count only the user's part and list what they're made of`() {
        answer("how much on food this month").let {
            assertThat(it.text).isEqualTo("₹645 on Food & Canteen and Food Delivery this month, over 3 payments.")
            assertThat(it.lines.first().title).isEqualTo("Swiggy")
        }
        assertThat(answer("how many times did I order from swiggy").text).isEqualTo("2 payments on Swiggy this month, ₹605 in all.")
        assertThat(answer("biggest payment this month").text).isEqualTo("The biggest this month was ₹450 to Swiggy.")
        assertThat(answer("top categories this month").text).isEqualTo("Most went on Food Delivery this month: ₹605 of ₹765.")
        assertThat(answer("how much on zomato").text).isEqualTo("Nothing spent on Zomato this month.")
        assertThat(answer("average per day this week").text).isEqualTo("About ₹127.50 a day this week (₹765 over 6 days).")
    }

    @Test
    fun `a friend's balance`() {
        val rohan = FriendEntity("f1", "Rohan Mehta", ownerUid = "u", updatedAt = 0)
        val iou = IouEntity("i", "f1", 150_00, theyOwe = true, note = "Auto", timestamp = 0, ownerUid = "u", updatedAt = 0)
        val text = answer("how much does rohan owe me", listOf(FriendBalance(rohan, 150_00, emptyList(), listOf(iou)))).text
        assertThat(text).isEqualTo("Rohan owes you ₹150.")
    }
}
