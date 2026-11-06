package com.mehul.inkless

import android.app.Application
import com.mehul.inkless.capture.sms.SmsCapture
import com.mehul.inkless.session.SessionManager
import com.mehul.inkless.sync.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class InklessApp : Application() {

    @Inject lateinit var sessionManager: SessionManager

    @Inject lateinit var smsCapture: SmsCapture

    @Inject lateinit var syncScheduler: SyncScheduler

    override fun onCreate() {
        super.onCreate()
        sessionManager.start()
        smsCapture.start()
        syncScheduler.start()
    }
}
