package app.vibeyfin.data.discovery

/**
 * Keeps the radio awake for broadcast traffic while a search runs. Android's Wi-Fi hardware
 * filters broadcasts to save power unless an app holds a multicast lock; other platforms don't
 * need one.
 */
interface MulticastLock {
    /** Holds the lock while [block] runs and releases it afterwards, however [block] ends. */
    suspend fun <T> withLock(block: suspend () -> T): T
}
