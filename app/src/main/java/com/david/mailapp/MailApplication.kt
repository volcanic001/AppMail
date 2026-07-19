package com.david.mailapp

import android.app.Application
import com.david.mailapp.core.di.AppContainer

class MailApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AppContainer.init(this)
    }
}
