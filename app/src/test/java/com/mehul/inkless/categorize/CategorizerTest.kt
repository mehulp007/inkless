package com.mehul.inkless.categorize

import com.google.common.truth.Truth.assertThat
import com.mehul.inkless.data.seed.CategoryIds
import com.mehul.inkless.ml.Guess
import org.junit.Test

class CategorizerTest {

    private fun rule(
        pattern: String,
        category: String,
        field: RuleField = RuleField.EITHER,
        match: RuleMatch = RuleMatch.CONTAINS,
        priority: Int = 0,
    ) = CategoryRule(field, match, pattern, category, priority)

    @Test
    fun `a rule beats merchant memory, which beats keywords`() {
        val vpa = "swiggy@icici"
        val rules = listOf(rule("swiggy", CategoryIds.FUN))

        assertThat(Categorizer.decide(vpa, null, rules, CategoryIds.GIFTS)!!.source).isEqualTo(DecisionSource.RULE)
        assertThat(Categorizer.decide(vpa, null, rules, CategoryIds.GIFTS)!!.categoryId).isEqualTo(CategoryIds.FUN)

        val fromMemory = Categorizer.decide(vpa, null, emptyList(), CategoryIds.GIFTS)!!
        assertThat(fromMemory.source).isEqualTo(DecisionSource.MERCHANT_MEMORY)
        assertThat(fromMemory.categoryId).isEqualTo(CategoryIds.GIFTS)

        val fromKeyword = Categorizer.decide(vpa, null, emptyList(), null)!!
        assertThat(fromKeyword.source).isEqualTo(DecisionSource.KEYWORD)
        assertThat(fromKeyword.categoryId).isEqualTo(CategoryIds.FOOD_DELIVERY)
    }

    @Test
    fun `nothing known means review`() {
        assertThat(Categorizer.decide("q829154@ybl", null, emptyList(), null)).isNull()
        assertThat(Categorizer.decide(null, "RAVI KUMAR", emptyList(), null)).isNull()
    }

    @Test
    fun `keyword guesses are confident enough to auto-file, memory and rules are certain`() {
        assertThat(Keywords.CONFIDENCE).isAtLeast(Categorizer.AUTO_FILE_THRESHOLD)
        assertThat(Categorizer.decide(null, null, emptyList(), CategoryIds.FUN)!!.confidence).isEqualTo(1f)
    }

    @Test
    fun `rules run in priority order`() {
        val rules = listOf(
            rule("canteen", CategoryIds.FOOD_CANTEEN, priority = 2),
            rule("block c", CategoryIds.LAUNDRY, priority = 1),
        )
        val decision = Categorizer.decide(null, "Block C Canteen", rules, null)!!
        assertThat(decision.categoryId).isEqualTo(CategoryIds.LAUNDRY)
    }

    @Test
    fun `rule fields and match types`() {
        val upiOnly = listOf(rule("canteen", CategoryIds.FOOD_CANTEEN, field = RuleField.UPI_ID))
        assertThat(Categorizer.decide("q123@ybl", "SHARMA CANTEEN", upiOnly, null)?.source).isNotEqualTo(DecisionSource.RULE)
        assertThat(Categorizer.decide("canteen.blockc@ybl", null, upiOnly, null)?.source).isEqualTo(DecisionSource.RULE)

        val exact = listOf(rule("ravi kumar", CategoryIds.LENT, field = RuleField.PAYEE, match = RuleMatch.EXACT))
        assertThat(Categorizer.decide(null, "RAVI KUMAR", exact, null)?.categoryId).isEqualTo(CategoryIds.LENT)
        assertThat(Categorizer.decide(null, "RAVI KUMAR SHARMA", exact, null)).isNull()

        val regex = listOf(rule("""^q\d+@ybl$""", CategoryIds.FOOD_CANTEEN, field = RuleField.UPI_ID, match = RuleMatch.REGEX))
        assertThat(Categorizer.decide("q829154@ybl", null, regex, null)?.categoryId).isEqualTo(CategoryIds.FOOD_CANTEEN)
    }

    @Test
    fun `a broken regex never matches instead of crashing`() {
        val broken = listOf(rule("([", CategoryIds.FUN, match = RuleMatch.REGEX))
        assertThat(Categorizer.decide("abc@ybl", "abc", broken, null)).isNull()
    }

    @Test
    fun `keywords match whole short words and the start of long ones`() {
        assertThat(Keywords.categoryFor("olacabs@ybl", null)).isEqualTo(CategoryIds.TRAVEL)
        assertThat(Keywords.categoryFor(null, "OLA")).isEqualTo(CategoryIds.TRAVEL)
        assertThat(Keywords.categoryFor(null, "KOLAPUR STORES")).isNull()
        assertThat(Keywords.categoryFor(null, "CHAITANYA S")).isNull()
        assertThat(Keywords.categoryFor(null, "Chai Sutta Techn")).isEqualTo(CategoryIds.FOOD_CANTEEN)
        assertThat(Keywords.categoryFor("swiggyinstamart@icici", null)).isEqualTo(CategoryIds.GROCERIES)
        assertThat(Keywords.categoryFor("swiggy.stores@axb", null)).isEqualTo(CategoryIds.FOOD_DELIVERY)
        assertThat(Keywords.categoryFor(null, "CRUST AND CO CAFE")).isEqualTo(CategoryIds.FOOD_CANTEEN)
        assertThat(Keywords.categoryFor("jiomart@sbi", null)).isEqualTo(CategoryIds.GROCERIES)
        assertThat(Keywords.categoryFor("jio@hdfc", null)).isEqualTo(CategoryIds.RECHARGE)
        assertThat(Keywords.categoryFor(null, "District Hospital")).isEqualTo(CategoryIds.HEALTH)
    }

    @Test
    fun `a sure model beats keywords but not memory, and an unsure one is ignored`() {
        val swiggy = "swiggy.upi@axisbank"
        val sure = Guess(CategoryIds.GROCERIES, 0.92f)
        assertThat(Categorizer.decide(swiggy, null, emptyList(), null, sure)!!.source).isEqualTo(DecisionSource.MODEL)
        assertThat(Categorizer.decide(swiggy, null, emptyList(), CategoryIds.FUN, sure)!!.source).isEqualTo(DecisionSource.MERCHANT_MEMORY)
        val unsure = Guess(CategoryIds.GROCERIES, 0.6f)
        assertThat(Categorizer.decide(swiggy, null, emptyList(), null, unsure)!!.source).isEqualTo(DecisionSource.KEYWORD)
        assertThat(Categorizer.decide("q829154@ybl", null, emptyList(), null, unsure)).isNull()
    }

    @Test
    fun `countMatches counts the payments a rule would pick, ignoring case`() {
        val payments = listOf(
            "swiggy@icici" to "Swiggy",
            "canteen.blockc@okaxis" to "SHARMA CANTEEN",
            null to "Campus Canteen",
            "q829154@ybl" to null,
        )
        assertThat(Categorizer.countMatches(rule("canteen", CategoryIds.FOOD_CANTEEN), payments)).isEqualTo(2)
        assertThat(Categorizer.countMatches(rule("canteen", CategoryIds.FOOD_CANTEEN, field = RuleField.UPI_ID), payments)).isEqualTo(1)
        assertThat(Categorizer.countMatches(rule("swiggy", CategoryIds.FOOD_DELIVERY, match = RuleMatch.EXACT), payments)).isEqualTo(1)
        assertThat(Categorizer.countMatches(rule("""^(swiggy|q\d+)@""",CategoryIds.FOOD_DELIVERY, field = RuleField.UPI_ID, match = RuleMatch.REGEX), payments)).isEqualTo(2)
        assertThat(Categorizer.countMatches(rule("[", CategoryIds.FOOD_DELIVERY, match = RuleMatch.REGEX), payments)).isEqualTo(0)
    }
}
