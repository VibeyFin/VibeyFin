package com.liamgleeson.vibeyfin

import android.app.Application
import com.liamgleeson.vibeyfin.di.initKoin
import org.koin.android.ext.koin.androidContext

class VibeyFinApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@VibeyFinApplication)
        }
    }
}
