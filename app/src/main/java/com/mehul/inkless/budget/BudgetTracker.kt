package com.mehul.inkless.budget

import com.mehul.inkless.data.db.InklessDatabase
import com.mehul.inkless.data.db.SpendPart
import com.mehul.inkless.data.db.entity.BudgetEntity
import com.mehul.inkless.data.repo.LedgerRepository
import com.mehul.inkless.data.seed.CategoryIds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** The budget as it stands right now, for Home, the widget and the weekly summary. */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class BudgetTracker @Inject constructor(
    private val repository: LedgerRepository,
    private val clock: Clock,
) {
    /** Null while no budget is set. */
    fun snapshot(): Flow<BudgetSnapshot?> = repository.budget().flatMapLatest { budget ->
        if (budget == null) return@flatMapLatest flowOf(null)
        val cycle = BudgetMath.cycle(LocalDate.now(clock), budget.allowanceDay)
        repository.spendBetween(cycle.startMillis(clock.zone), cycle.endMillis(clock.zone)).map { parts ->
            snapshotOf(budget, parts)
        }
    }

    /** The same, read once from [db] (for work done outside a screen). */
    suspend fun current(db: InklessDatabase): BudgetSnapshot? {
        val budget = db.budgetDao().get() ?: return null
        val cycle = BudgetMath.cycle(LocalDate.now(clock), budget.allowanceDay)
        return snapshotOf(budget, db.spendDao().between(cycle.startMillis(clock.zone), cycle.endMillis(clock.zone)))
    }

    private fun snapshotOf(budget: BudgetEntity, parts: List<SpendPart>) =
        BudgetMath.snapshot(
            budgetPaise = budget.amountPaise,
            allowanceDay = budget.allowanceDay,
            dailyFoodCapPaise = budget.dailyFoodCapPaise,
            parts = parts,
            foodCategoryIds = FOOD,
            today = LocalDate.now(clock),
            zone = clock.zone,
        )

    companion object {
        /** Eating out: what the food cap and "mess skipped" days count. */
        val FOOD = setOf(CategoryIds.FOOD_CANTEEN, CategoryIds.FOOD_DELIVERY)
    }
}
