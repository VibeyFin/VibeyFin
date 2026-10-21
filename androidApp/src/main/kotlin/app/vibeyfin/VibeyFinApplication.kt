package app.vibeyfin

import android.app.Application
import app.vibeyfin.di.initKoin
import org.koin.android.ext.koin.androidContext

class VibeyFinApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@VibeyFinApplication)
        }
    }
}
