package app.vibeyfin.tv

import android.app.Application
import app.vibeyfin.di.initKoin
import org.koin.android.ext.koin.androidContext

class VibeyFinTvApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@VibeyFinTvApplication)
        }
    }
}
