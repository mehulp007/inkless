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
        // No accounts yet, so the app always opens in demo mode.
        sessionManager.startDemo()
    }
}
