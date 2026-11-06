package com.mehul.inkless.session

import com.mehul.inkless.data.db.DatabaseProvider
import com.mehul.inkless.demo.Demo
import com.mehul.inkless.demo.DemoSeeder
import com.mehul.inkless.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManager @Inject constructor(
    private val databases: DatabaseProvider,
    private val demoSeeder: DemoSeeder,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    fun startDemo() {
        val db = databases.open(Demo.DATABASE_NAME)
        scope.launch { demoSeeder.prepare(db) }
    }
}
