package com.mehul.inkless.data.db

import android.content.Context
import androidx.room.Room
import com.mehul.inkless.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Every account gets its own database file (`inkless_<name>.db`), so two people sharing a phone
 * never see each other's data. Repositories follow [current] and switch when it changes.
 */
@Singleton
class DatabaseProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val _current = MutableStateFlow<InklessDatabase?>(null)
    val current: StateFlow<InklessDatabase?> = _current.asStateFlow()

    private val closing = mutableMapOf<String, Job>()

    @Synchronized
    fun open(name: String): InklessDatabase {
        val fileName = fileName(name)
        val previous = _current.value
        if (previous != null && previous.openHelper.databaseName == fileName) return previous

        val db = Room.databaseBuilder(context, InklessDatabase::class.java, fileName).build()
        _current.value = db
        previous?.let(::retire)
        return db
    }

    @Synchronized
    fun close() {
        _current.value?.let(::retire)
        _current.value = null
    }

    /** Closes the database if it's open and deletes its file. */
    suspend fun delete(name: String) {
        val fileName = fileName(name)
        val pending = synchronized(this) {
            _current.value?.takeIf { it.openHelper.databaseName == fileName }?.let(::retire)
            _current.value = _current.value?.takeIf { it.openHelper.databaseName != fileName }
            closing[fileName]
        }
        pending?.join()
        context.deleteDatabase(fileName)
    }

    // Screens still collecting from the old database need a moment to switch over before it closes
    // under them; otherwise an in-flight query can hit a closed connection.
    private fun retire(db: InklessDatabase) {
        val fileName = db.openHelper.databaseName ?: return db.close()
        closing[fileName] = scope.launch {
            delay(CLOSE_GRACE_MS)
            db.close()
            synchronized(this@DatabaseProvider) { closing.remove(fileName) }
        }
    }

    companion object {
        private const val CLOSE_GRACE_MS = 1_000L

        fun fileName(name: String) = "inkless_$name.db"
    }
}
