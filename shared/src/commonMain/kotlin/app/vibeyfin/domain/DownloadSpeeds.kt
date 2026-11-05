package app.vibeyfin.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** How fast the running downloads are coming in right now, kept in memory only, since a speed means nothing after a restart. Keyed by the download's folder (server, profile and item). */
class DownloadSpeeds {
    private val _speeds = MutableStateFlow<Map<String, Long>>(emptyMap())
    val speeds: StateFlow<Map<String, Long>> = _speeds

    fun set(key: String, bytesPerSecond: Long) = _speeds.update { it + (key to bytesPerSecond) }

    fun clear(key: String) = _speeds.update { it - key }
}
