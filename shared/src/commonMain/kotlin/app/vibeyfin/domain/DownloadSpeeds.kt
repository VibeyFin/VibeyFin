package app.vibeyfin.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** What the running downloads are doing right now, kept in memory only, since a speed means nothing after a restart: how fast a video comes in, and how far its conversion is. Keyed by the download's folder (server, profile and item). */
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
