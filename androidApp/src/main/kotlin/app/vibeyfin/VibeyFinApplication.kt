package app.vibeyfin

import android.app.Application
import app.vibeyfin.app.notifications.NewTitlesScheduler
import app.vibeyfin.data.db.AccountDao
import app.vibeyfin.di.ApplicationScope
import app.vibeyfin.di.initKoin
import kotlinx.coroutines.CoroutineScope
import org.koin.android.ext.koin.androidContext

class VibeyFinApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val koin = initKoin {
            androidContext(this@VibeyFinApplication)
        }.koin
        // The new-title work runs while any profile gets notifications, and is cancelled once none does.
        NewTitlesScheduler.follow(this, koin.get<AccountDao>().observeNotifiableProfileCount(), koin.get<CoroutineScope>(ApplicationScope))
    }
}
