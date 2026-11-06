package com.mehul.inkless.data.db

import android.content.Context
import androidx.room.Room
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Every account gets its own database file (`inkless_<name>.db`), so two people sharing a phone
 * never see each other's data. Repositories follow [current] and switch when it changes.
 */
@Singleton
class DatabaseProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val _current = MutableStateFlow<InklessDatabase?>(null)
    val current: StateFlow<InklessDatabase?> = _current.asStateFlow()

    @Synchronized
    fun open(name: String): InklessDatabase {
        val fileName = fileName(name)
        val previous = _current.value
        if (previous != null && previous.openHelper.databaseName == fileName) return previous

        val db = Room.databaseBuilder(context, InklessDatabase::class.java, fileName).build()
        _current.value = db
        previous?.close()
        return db
    }

    @Synchronized
    fun close() {
        _current.value?.close()
        _current.value = null
    }

    companion object {
        fun fileName(name: String) = "inkless_$name.db"
    }
}
