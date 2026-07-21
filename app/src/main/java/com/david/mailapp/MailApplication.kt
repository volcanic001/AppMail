package com.david.mailapp

import android.app.Application
import com.david.mailapp.core.di.AppContainer
import kotlinx.coroutines.launch

class MailApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AppContainer.init(this)

        // Subscribe to reauthentication events in application scope (survives Activity recreation).
        AppContainer.applicationScope.launch {
            com.david.mailapp.core.di.GlobalReauthenticationCollector(
                reauthenticationEvents = AppContainer.oauthTokenManager.reauthenticationEvents,
                invalidateExpiredSession = { AppContainer.invalidateExpiredSession() },
                sessionExpiredSignal = AppContainer.sessionExpiredSignal,
                errorLogger = { android.util.Log.e("MailApplication", "Error in global invalidation", it) }
            ).collectEvents()
        }
    }
}
