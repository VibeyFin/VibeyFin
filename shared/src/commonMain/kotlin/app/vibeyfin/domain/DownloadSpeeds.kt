package app.vibeyfin.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * How fast each running download is coming in, in bytes a second, kept in memory only: a speed is true for a moment and means
 * nothing after a restart, so it is not worth a column. [DownloadRunner] writes it and the screens read it, to say how long is
 * left. Keyed by [folder] (server, profile and item).
 */
class DownloadSpeeds {
    private val _speeds = MutableStateFlow<Map<String, Long>>(emptyMap())
    val speeds: StateFlow<Map<String, Long>> = _speeds

    fun set(key: String, bytesPerSecond: Long) = _speeds.update { it + (key to bytesPerSecond) }

    fun clear(key: String) = _speeds.update { it - key }
}
