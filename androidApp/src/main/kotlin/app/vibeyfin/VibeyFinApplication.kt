package app.vibeyfin

import android.app.Application
import app.vibeyfin.app.notifications.NewTitlesScheduler
import app.vibeyfin.data.db.AccountDao
import app.vibeyfin.di.ApplicationScope
import app.vibeyfin.di.initKoin
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import kotlinx.coroutines.CoroutineScope
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext

// OkHttp loads at most 5 pictures from one host at a time; a screen of Home asks the server for about 20 at once.
private const val IMAGE_REQUESTS_PER_HOST = 16

class VibeyFinApplication :
    Application(),
    SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()

        val koin = initKoin {
            androidContext(this@VibeyFinApplication)
        }.koin

        // The new-title work runs while any profile gets notifications, and is cancelled once none does
        NewTitlesScheduler.follow(this, koin.get<AccountDao>().observeNotifiableProfileCount(), koin.get<CoroutineScope>(ApplicationScope))
    }

    // Coil's defaults (memory and disk cache) with more pictures loading side by side
    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components {
            add(
                OkHttpNetworkFetcherFactory(
                    callFactory = { OkHttpClient.Builder().dispatcher(Dispatcher().apply { maxRequestsPerHost = IMAGE_REQUESTS_PER_HOST }).build() },
                ),
            )
        }
        .build()
}
