package app.vibeyfin.ios

import app.vibeyfin.data.download.IosBackgroundTransfer
import app.vibeyfin.di.initKoin
import app.vibeyfin.domain.ExternalVideoTransfer
import org.koin.mp.KoinPlatform

/** Called once from the SwiftUI app's `init`. Named without an `init` prefix, which Obj-C export rewrites. */
fun startKoinIos() {
    if (KoinPlatform.getKoinOrNull() == null) initKoin()
}

/**
 * The system woke the app for the background session [identifier] (a download ended while the app was away). Called from the app
 * delegate, with the system's [completion], which is called once the session has been told what happened. Starts the app's
 * shared code first if the wake-up got here before the app's own start.
 */
fun handleBackgroundDownloadEvents(identifier: String, completion: () -> Unit) {
    startKoinIos()
    (KoinPlatform.getKoin().get<ExternalVideoTransfer>() as IosBackgroundTransfer).handleBackgroundEvents(identifier, completion)
}
