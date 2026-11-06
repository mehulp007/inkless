package com.mehul.inkless

import android.app.Application
import com.mehul.inkless.session.SessionManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class InklessApp : Application() {

    @Inject lateinit var sessionManager: SessionManager

    override fun onCreate() {
        super.onCreate()
        sessionManager.start()
    }
}
