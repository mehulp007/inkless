package com.mehul.inkless.ml

import com.mehul.inkless.data.db.DatabaseProvider
import com.mehul.inkless.data.db.InklessDatabase
import com.mehul.inkless.data.db.dao.TrainingRow
import com.mehul.inkless.data.db.entity.ModelCountEntity
import com.mehul.inkless.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZoneId
import java.util.WeakHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class ModelLook(val best: Guess?, val trusted: Boolean)

/**
 * The category model for whichever account is open. It retrains a moment after any payment's
 * category changes (a correction, a confirmed guess, a new rule filing things), so the next guess
 * already knows. Training on a few thousand payments takes milliseconds, so it simply starts over
 * from the payments each time instead of keeping track of what changed.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Singleton
class CategoryModel @Inject constructor(
    private val databases: DatabaseProvider,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val lock = Mutex()
    private val loaded = WeakHashMap<InklessDatabase, NaiveBayesCategorizer>()

    fun start() {
        scope.launch {
            databases.current.filterNotNull()
                .flatMapLatest { db -> db.insightDao().observeTrainingRows().map { db to it } }
                .debounce(RETRAIN_AFTER_MS)
                .collectLatest { (db, rows) -> retrain(db, rows) }
        }
    }

    suspend fun retrain(db: InklessDatabase, rows: List<TrainingRow>) {
        val model = train(rows, ZoneId.systemDefault())
        db.insightDao().replaceCounts(model.counts().map { ModelCountEntity(it.feature, it.categoryId, it.count) })
        lock.withLock { loaded[db] = model }
    }

    /** The model as last trained, read from its table the first time (e.g. a capture with the app closed). */
    suspend fun of(db: InklessDatabase): NaiveBayesCategorizer = lock.withLock {
        loaded.getOrPut(db) {
            NaiveBayesCategorizer.fromCounts(db.insightDao().counts().map { FeatureCount(it.feature, it.categoryId, it.count) })
        }
    }

    /** The model's guesses for a payment, most likely first. */
    suspend fun guesses(db: InklessDatabase, vpa: String?, payee: String?, amountPaise: Long, timestamp: Long): List<Guess> =
        of(db).predict(FeatureExtractor.of(vpa, payee, amountPaise, timestamp, ZoneId.systemDefault()))

    /** The model's view of a payment: its best guess, and whether it has learned enough to file it on its own. */
    suspend fun look(db: InklessDatabase, vpa: String?, payee: String?, amountPaise: Long, timestamp: Long): ModelLook {
        val model = of(db)
        val best = model.predict(FeatureExtractor.of(vpa, payee, amountPaise, timestamp, ZoneId.systemDefault())).firstOrNull()
        return ModelLook(best, trusted = best != null && ModelTrust.canAutoFile(model, best))
    }

    companion object {
        private const val RETRAIN_AFTER_MS = 1_500L

        fun train(rows: List<TrainingRow>, zone: ZoneId): NaiveBayesCategorizer = NaiveBayesCategorizer().apply {
            rows.forEach { learn(FeatureExtractor.of(it.vpa, it.payeeRaw, it.amountPaise, it.timestamp, zone), it.categoryId) }
        }
    }
}
