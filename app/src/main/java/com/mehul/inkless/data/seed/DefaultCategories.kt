package com.mehul.inkless.data.seed

import com.mehul.inkless.data.db.entity.CategoryEntity

/** Fixed ids so the same built-in category lines up across devices once sync exists. */
object CategoryIds {
    const val FOOD_CANTEEN = "food_canteen"
    const val FOOD_DELIVERY = "food_delivery"
    const val GROCERIES = "groceries"
    const val TRAVEL = "travel"
    const val RECHARGE = "recharge"
    const val COLLEGE = "college"
    const val LAUNDRY = "laundry"
    const val FUN = "fun"
    const val SHOPPING = "shopping"
    const val HEALTH = "health"
    const val GIFTS = "gifts"
    const val LENT = "lent"
    const val OTHER = "other"
}

object DefaultCategories {

    private data class Spec(val id: String, val name: String, val emoji: String, val color: Long)

    private val specs = listOf(
        Spec(CategoryIds.FOOD_CANTEEN, "Food & Canteen", "🍔", 0xFFF29D38),
        Spec(CategoryIds.FOOD_DELIVERY, "Food Delivery", "🍜", 0xFFE4572E),
        Spec(CategoryIds.GROCERIES, "Groceries & Essentials", "🛒", 0xFF5DAA44),
        Spec(CategoryIds.TRAVEL, "Travel", "🚕", 0xFF3B82F6),
        Spec(CategoryIds.RECHARGE, "Recharge & Subscriptions", "📱", 0xFF8B5CF6),
        Spec(CategoryIds.COLLEGE, "College & Stationery", "📚", 0xFF0EA5A4),
        Spec(CategoryIds.LAUNDRY, "Laundry & Hostel", "🧺", 0xFF38BDF8),
        Spec(CategoryIds.FUN, "Fun & Outings", "🎉", 0xFFEC4899),
        Spec(CategoryIds.SHOPPING, "Shopping", "🛍️", 0xFF6366F1),
        Spec(CategoryIds.HEALTH, "Health", "💊", 0xFFEF4444),
        Spec(CategoryIds.GIFTS, "Gifts", "🎁", 0xFFD946EF),
        Spec(CategoryIds.LENT, "Lent to Friends", "💸", 0xFFCA8A04),
        Spec(CategoryIds.OTHER, "Other", "❓", 0xFF78716C),
    )

    fun create(ownerUid: String, nowMillis: Long): List<CategoryEntity> =
        specs.mapIndexed { index, spec ->
            CategoryEntity(
                id = spec.id,
                name = spec.name,
                emoji = spec.emoji,
                colorArgb = spec.color,
                sortOrder = index,
                isSystem = true,
                ownerUid = ownerUid,
                updatedAt = nowMillis,
            )
        }
}
