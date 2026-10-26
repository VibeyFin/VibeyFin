package app.vibeyfin.domain

/**
 * What the network must be before the platform starts the download work.
 *
 * Work that must keep running through a lost connection must not ask the platform for a network: Android stops work the moment
 * its network constraint is lost, and starts it again later in the background, where it may not run as a foreground service.
 * So the work asked for now is [None] and waits for the network itself ([DownloadRunner.run]); [Any] and [Unmetered] are for
 * work asked for after a run has waited long enough and given up.
 */
enum class DownloadNetwork {
    /** No condition: start now. The runner waits for what it needs. */
    None,

    /** Any connection. */
    Any,

    /** An unmetered one (Wi-Fi), because no download waiting is allowed to use mobile data. */
    Unmetered,
}

/**
 * Asks the platform to run [DownloadRunner] in the background, once [network] is what is needed, even if the app is
 * closed or the screen is off (Android: WorkManager with a foreground service; iOS: a background URLSession).
 * Asking while it already runs is harmless.
 */
interface DownloadScheduler {
    /** False on a platform that cannot download yet, so nothing is queued that would never run. */
    val isSupported: Boolean

    fun schedule(network: DownloadNetwork)
}

object NoDownloadScheduler : DownloadScheduler {
    override val isSupported = false

    override fun schedule(network: DownloadNetwork) = Unit
}
