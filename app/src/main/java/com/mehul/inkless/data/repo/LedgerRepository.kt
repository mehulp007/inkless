package com.mehul.inkless.data.repo

import com.mehul.inkless.data.db.DatabaseProvider
import com.mehul.inkless.data.db.InklessDatabase
import com.mehul.inkless.data.db.dao.CategoryTotal
import com.mehul.inkless.data.db.dao.TransactionListItem
import com.mehul.inkless.data.db.entity.CategoryEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class LedgerRepository @Inject constructor(
    private val databases: DatabaseProvider,
) {
    // Re-subscribes whenever a different user's database is opened.
    private fun <T> fromCurrentDb(query: (InklessDatabase) -> Flow<T>): Flow<T> =
        databases.current.filterNotNull().flatMapLatest(query)

    fun categories(): Flow<List<CategoryEntity>> =
        fromCurrentDb { it.categoryDao().observeAll() }

    fun transactionsBetween(startMillis: Long, endMillis: Long): Flow<List<TransactionListItem>> =
        fromCurrentDb { it.transactionDao().observeBetween(startMillis, endMillis) }

    fun categoryTotalsBetween(startMillis: Long, endMillis: Long): Flow<List<CategoryTotal>> =
        fromCurrentDb { it.transactionDao().observeCategoryTotals(startMillis, endMillis) }
}
