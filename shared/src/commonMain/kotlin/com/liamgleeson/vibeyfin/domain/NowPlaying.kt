package com.liamgleeson.vibeyfin.domain

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class NowPlayingState(
    val itemId: String,
    val title: String,
    val subtitle: String?,
    val playing: Boolean,
    val positionMs: Long,
)

/**
 * What the player is showing right now, for the mini player to draw and to reopen the full player.
 * A Koin `single`, safe to call from any thread.
 */
class NowPlaying {
    private val _state = MutableStateFlow<NowPlayingState?>(null)
    val state: StateFlow<NowPlayingState?> = _state.asStateFlow()

    // No replay: a restore request is an event, and a collector that arrives later (a screen
    // opened after the tap) must not reopen the player by surprise. The buffer keeps `tryEmit`
    // from ever dropping a request while a collector is present but briefly busy.
    private val _restoreRequests = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val restoreRequests: SharedFlow<String> = _restoreRequests.asSharedFlow()

    fun update(state: NowPlayingState) = _state.update { state }

    fun clear() = _state.update { null }

    /** Asks the UI to bring the full player back for the current item. Does nothing when idle. */
    fun requestRestore() {
        val current = _state.value ?: return
        _restoreRequests.tryEmit(current.itemId)
    }
}
