package app.vibeyfin

import android.app.Application
import app.vibeyfin.app.i18n.LanguageRefresh
import app.vibeyfin.app.notifications.NewTitlesScheduler
import app.vibeyfin.app.notifications.NotificationChannels
import app.vibeyfin.data.db.AccountDao
import app.vibeyfin.di.ApplicationScope
import app.vibeyfin.di.initKoin
import app.vibeyfin.i18n.Texts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext

class VibeyFinApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val koin = initKoin {
            androidContext(this@VibeyFinApplication)
        }.koin

        // The new-title work runs while any profile gets notifications, and is cancelled once none does
        val applicationScope: CoroutineScope = koin.get<CoroutineScope>(ApplicationScope)

        LanguageRefresh.start(applicationScope)
        applicationScope.launch {
            Texts.language.drop(1).collect { NotificationChannels.rename(this@VibeyFinApplication) }
        }
        NewTitlesScheduler.follow(this, koin.get<AccountDao>().observeNotifiableProfileCount(), applicationScope)
    }
}
