package app.vibeyfin.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * How fast each running download is coming in, in bytes a second, kept in memory only: a speed is true for a moment and means
 * nothing after a restart, so it is not worth a column. [DownloadRunner] writes it and the screens read it, to say how long is
 * left. Keyed by [folder] (server, profile and item).
 */
/** What the running downloads are doing right now, kept in memory only: how fast a video comes in, and how far its conversion is. Keyed by the download's folder. */
class DownloadSpeeds {
    private val _converting = MutableStateFlow<Map<String, Float>>(emptyMap())

    /** The downloads being made playable on the device, with how far each is, 0 to 1. */
    val converting: StateFlow<Map<String, Float>> = _converting

    fun setConverting(key: String, fraction: Float) = _converting.update { it + (key to fraction.coerceIn(0f, 1f)) }

    fun clearConverting(key: String) = _converting.update { it - key }

    private val _speeds = MutableStateFlow<Map<String, Long>>(emptyMap())
    val speeds: StateFlow<Map<String, Long>> = _speeds

    fun set(key: String, bytesPerSecond: Long) = _speeds.update { it + (key to bytesPerSecond) }

    fun clear(key: String) = _speeds.update { it - key }
}
