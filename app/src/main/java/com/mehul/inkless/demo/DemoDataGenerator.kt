package com.mehul.inkless.demo

import com.mehul.inkless.data.db.entity.CaptureSource
import com.mehul.inkless.data.db.entity.Direction
import com.mehul.inkless.data.db.entity.MerchantEntity
import com.mehul.inkless.data.db.entity.SyncState
import com.mehul.inkless.data.db.entity.TransactionEntity
import com.mehul.inkless.data.db.entity.TransactionStatus
import com.mehul.inkless.data.db.entity.TransactionType
import com.mehul.inkless.data.seed.CategoryIds
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.random.Random

data class DemoData(
    val merchants: List<MerchantEntity>,
    val transactions: List<TransactionEntity>,
)

/**
 * Makes up a few months of believable hostel spending for screenshots and "Try demo": chai most
 * evenings, canteen breakfasts, weekend Swiggy, autos, a recharge on the 3rd and the allowance from
 * home on the 1st. The same seed and the same `now` always give exactly the same data.
 */
class DemoDataGenerator(private val seed: Long = DEFAULT_SEED) {

    private data class Shop(val vpa: String, val name: String, val categoryId: String?)

    private data class Draft(
        val timestamp: Long,
        val shop: Shop,
        val amountPaise: Long,
        val direction: Direction,
        val type: TransactionType,
    )

    private data class PendingRefund(val day: LocalDate, val shop: Shop, val amountPaise: Long)

    fun generate(now: ZonedDateTime, ownerUid: String = Demo.OWNER_UID, monthsBack: Int = 2): DemoData {
        val random = Random(seed)
        val zone = now.zone
        val nowMillis = now.toInstant().toEpochMilli()
        val today = now.toLocalDate()
        val firstDay = today.withDayOfMonth(1).minusMonths(monthsBack.toLong())

        val drafts = mutableListOf<Draft>()
        val refunds = mutableListOf<PendingRefund>()

        fun timeOn(day: LocalDate, fromHour: Int, toHour: Int): Long {
            val minuteOfDay = random.nextInt(fromHour * 60, toHour * 60)
            return day.atStartOfDay(zone)
                .plusMinutes(minuteOfDay.toLong())
                .plusSeconds(random.nextInt(60).toLong())
                .toInstant()
                .toEpochMilli()
        }

        fun record(timestamp: Long, shop: Shop, amountPaise: Long, direction: Direction, type: TransactionType) {
            if (timestamp <= nowMillis) drafts += Draft(timestamp, shop, amountPaise, direction, type)
        }

        fun pay(
            day: LocalDate,
            shop: Shop,
            hours: IntRange,
            rupees: IntRange,
            step: Int = 1,
            withPaise: Boolean = false,
        ): Long {
            val timestamp = timeOn(day, hours.first, hours.last)
            val whole = random.nextInt(rupees.first / step, rupees.last / step + 1) * step
            val paise = if (withPaise) random.nextInt(100) else 0
            val amount = whole * 100L + paise
            record(timestamp, shop, amount, Direction.DEBIT, TransactionType.EXPENSE)
            return amount
        }

        fun chance(p: Double) = random.nextDouble() < p

        var day = firstDay
        while (!day.isAfter(today)) {
            val weekend = day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY
            val partyNight = weekend || day.dayOfWeek == DayOfWeek.FRIDAY

            if (day.dayOfMonth == 1) {
                val allowance = (12_000L + if (chance(0.3)) 1_000L else 0L) * 100
                record(timeOn(day, 9, 12), HOME, allowance, Direction.CREDIT, TransactionType.INCOME)
            }
            if (day.dayOfMonth == 3) pay(day, JIO, 10..13, 349..349)
            if (day.dayOfMonth == 12) pay(day, SPOTIFY, 0..2, 59..59)
            if (day.dayOfWeek == DayOfWeek.MONDAY) pay(day, LAUNDRY, 8..11, 80..160, step = 10)

            // Food
            if (chance(0.5)) pay(day, CANTEEN, 8..10, 25..60, step = 5)
            if (chance(0.75)) pay(day, TAPRI, 16..19, 10..30, step = 5)
            if (chance(0.25)) pay(day, MESS_EXTRAS, 13..15, 20..80, step = 5)
            if (chance(0.15)) pay(day, JUICE, 11..18, 30..60, step = 10)
            if (chance(0.3)) pay(day, NIGHT_CANTEEN, 22..24, 40..150, step = 5)
            if (chance(if (partyNight) 0.35 else 0.06)) {
                pay(day, if (chance(0.55)) SWIGGY else ZOMATO, 20..23, 149..480, withPaise = true)
            }

            // Getting around
            if (chance(0.25)) pay(day, AUTO, 9..20, 30..80, step = 10)
            if (chance(0.1)) pay(day, RAPIDO, 9..22, 45..140)
            if (weekend && chance(0.2)) pay(day, UBER, 11..23, 150..400, withPaise = true)
            if (chance(0.012)) pay(day, IRCTC, 10..22, 300..900, withPaise = true)

            // Essentials and college
            if (chance(0.06)) pay(day, BLINKIT, 10..23, 150..450, withPaise = true)
            if (chance(0.03)) pay(day, ZEPTO, 10..23, 120..380, withPaise = true)
            if (chance(0.18)) pay(day, GENERAL_STORE, 9..21, 20..120, step = 5)
            if (!weekend && chance(0.18)) pay(day, XEROX, 9..17, 10..60, step = 2)
            if (chance(0.05)) pay(day, STATIONERY, 10..19, 40..250, step = 5)

            // Weekends out
            if (weekend && chance(0.18)) pay(day, PVR, 13..22, 220..450, step = 10)
            if (weekend && chance(0.08)) pay(day, BOOKMYSHOW, 12..21, 300..600, withPaise = true)
            if (weekend && chance(0.06)) pay(day, GAME_ZONE, 14..21, 200..400, step = 50)

            // Occasional bigger stuff
            listOf(AMAZON to 0.02, MYNTRA to 0.01).forEach { (shop, p) ->
                if (chance(p)) {
                    val amount = pay(day, shop, 10..24, if (shop == AMAZON) 299..1499 else 499..1999, withPaise = true)
                    if (chance(0.12)) refunds += PendingRefund(day.plusDays(random.nextLong(3, 7)), shop, amount)
                }
            }
            if (chance(0.005)) pay(day, DECATHLON, 12..20, 600..1800)
            if (chance(0.04)) pay(day, APOLLO, 9..22, 60..350, withPaise = true)
            if (chance(0.015)) pay(day, CLINIC, 9..17, 100..200, step = 50)
            if (chance(0.015)) pay(day, ARCHIES, 12..20, 150..600, step = 10)
            if (chance(0.06)) pay(day, FRIENDS[random.nextInt(FRIENDS.size)], 10..23, 100..500, step = 50)
            if (chance(0.02)) pay(day, UNKNOWN_QR, 9..22, 20..200, step = 5)

            refunds.filter { it.day == day }.forEach {
                record(timeOn(day, 10, 18), it.shop, it.amountPaise, Direction.CREDIT, TransactionType.REFUND)
            }

            day = day.plusDays(1)
        }

        val merchants = ALL_SHOPS.associateWith { shop ->
            MerchantEntity(
                id = UUID.nameUUIDFromBytes("demo:${shop.vpa}".toByteArray()).toString(),
                vpa = shop.vpa,
                displayName = shop.name,
                defaultCategoryId = shop.categoryId,
                timesSeen = 0,
                ownerUid = ownerUid,
                updatedAt = nowMillis,
                syncState = SyncState.SYNCED,
            )
        }

        var balance = STARTING_BALANCE_PAISE
        val transactions = drafts.sortedBy { it.timestamp }.map { draft ->
            balance += if (draft.direction == Direction.CREDIT) draft.amountPaise else -draft.amountPaise
            val confirmed = chance(0.25)
            TransactionEntity(
                id = UUID(random.nextLong(), random.nextLong()).toString(),
                amountPaise = draft.amountPaise,
                direction = draft.direction,
                type = draft.type,
                timestamp = draft.timestamp,
                vpa = draft.shop.vpa,
                payeeRaw = draft.shop.vpa,
                merchantId = merchants.getValue(draft.shop).id,
                accountLast4 = "4821",
                bankCode = "HDFC",
                upiRef = (100_000_000_000L + random.nextLong(900_000_000_000L)).toString(),
                balanceAfterPaise = balance,
                categoryId = draft.shop.categoryId,
                confidence = if (confirmed) 1f else 0.82f + random.nextFloat() * 0.17f,
                status = if (confirmed) TransactionStatus.CONFIRMED else TransactionStatus.AUTO,
                sources = if (chance(0.6)) setOf(CaptureSource.SMS, CaptureSource.NOTIFICATION) else setOf(CaptureSource.SMS),
                createdAt = draft.timestamp,
                updatedAt = draft.timestamp,
                ownerUid = ownerUid,
                syncState = SyncState.SYNCED,
            )
        }

        val seen = transactions.groupingBy { it.merchantId }.eachCount()
        val usedMerchants = merchants.values
            .filter { it.id in seen }
            .map { it.copy(timesSeen = seen.getValue(it.id)) }

        return DemoData(merchants = usedMerchants, transactions = transactions)
    }

    private companion object {
        const val DEFAULT_SEED = 2026L
        const val STARTING_BALANCE_PAISE = 3_800_00L

        val HOME = Shop("home.allowance@oksbi", "Home", null)

        val CANTEEN = Shop("sharmacanteen@okaxis", "Sharma Canteen", CategoryIds.FOOD_CANTEEN)
        val TAPRI = Shop("q829184562@ybl", "Tapri near Block C", CategoryIds.FOOD_CANTEEN)
        val MESS_EXTRAS = Shop("paytmqr2810050501011k3x9@paytm", "Mess Extras Counter", CategoryIds.FOOD_CANTEEN)
        val JUICE = Shop("q401928374@ybl", "Juice Corner", CategoryIds.FOOD_CANTEEN)
        val NIGHT_CANTEEN = Shop("nightcanteen.hostel@oksbi", "Night Canteen", CategoryIds.FOOD_CANTEEN)
        val SWIGGY = Shop("swiggy.upi@axisbank", "Swiggy", CategoryIds.FOOD_DELIVERY)
        val ZOMATO = Shop("zomato.order@hdfcbank", "Zomato", CategoryIds.FOOD_DELIVERY)

        val AUTO = Shop("q551203987@ybl", "Auto stand, Main Gate", CategoryIds.TRAVEL)
        val RAPIDO = Shop("rapido.bike@axl", "Rapido", CategoryIds.TRAVEL)
        val UBER = Shop("uber.india@icici", "Uber", CategoryIds.TRAVEL)
        val IRCTC = Shop("irctc.upi@sbi", "IRCTC", CategoryIds.TRAVEL)

        val BLINKIT = Shop("blinkit.payu@hdfcbank", "Blinkit", CategoryIds.GROCERIES)
        val ZEPTO = Shop("zeptonow@ybl", "Zepto", CategoryIds.GROCERIES)
        val GENERAL_STORE = Shop("q938271650@ybl", "Gupta General Store", CategoryIds.GROCERIES)

        val JIO = Shop("jioprepaid@jio", "Jio Prepaid", CategoryIds.RECHARGE)
        val SPOTIFY = Shop("spotify.upi@icici", "Spotify", CategoryIds.RECHARGE)

        val XEROX = Shop("q712093846@ybl", "Xerox Point", CategoryIds.COLLEGE)
        val STATIONERY = Shop("campusstationery@okicici", "Campus Stationery", CategoryIds.COLLEGE)
        val LAUNDRY = Shop("laundry.blockc@okaxis", "Hostel Laundry", CategoryIds.LAUNDRY)

        val PVR = Shop("pvrcinemas@icici", "PVR Cinemas", CategoryIds.FUN)
        val BOOKMYSHOW = Shop("bookmyshow@axisbank", "BookMyShow", CategoryIds.FUN)
        val GAME_ZONE = Shop("gamezone.mall@okhdfcbank", "Game Zone", CategoryIds.FUN)

        val AMAZON = Shop("amazonupi@apl", "Amazon", CategoryIds.SHOPPING)
        val MYNTRA = Shop("myntra@icici", "Myntra", CategoryIds.SHOPPING)
        val DECATHLON = Shop("decathlon.upi@hdfcbank", "Decathlon", CategoryIds.SHOPPING)

        val APOLLO = Shop("apollopharmacy@icici", "Apollo Pharmacy", CategoryIds.HEALTH)
        val CLINIC = Shop("q660194820@ybl", "Campus Clinic", CategoryIds.HEALTH)
        val ARCHIES = Shop("archies.gallery@okaxis", "Archies Gallery", CategoryIds.GIFTS)

        val FRIENDS = listOf(
            Shop("rohan.k07@oksbi", "Rohan", CategoryIds.LENT),
            Shop("aditya.s@okhdfcbank", "Aditya", CategoryIds.LENT),
            Shop("priya.m22@okicici", "Priya", CategoryIds.LENT),
        )
        val UNKNOWN_QR = Shop("q120938475@ybl", "q120938475@ybl", CategoryIds.OTHER)

        val ALL_SHOPS = listOf(
            HOME, CANTEEN, TAPRI, MESS_EXTRAS, JUICE, NIGHT_CANTEEN, SWIGGY, ZOMATO,
            AUTO, RAPIDO, UBER, IRCTC, BLINKIT, ZEPTO, GENERAL_STORE, JIO, SPOTIFY,
            XEROX, STATIONERY, LAUNDRY, PVR, BOOKMYSHOW, GAME_ZONE, AMAZON, MYNTRA,
            DECATHLON, APOLLO, CLINIC, ARCHIES, UNKNOWN_QR,
        ) + FRIENDS
    }
}
