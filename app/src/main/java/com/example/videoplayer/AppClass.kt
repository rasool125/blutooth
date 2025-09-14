package com.example.videoplayer

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext.startKoin

class AppClass : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@AppClass)
            modules(AppModule.getModule()) // IMPORTANT
        }
    }
}
