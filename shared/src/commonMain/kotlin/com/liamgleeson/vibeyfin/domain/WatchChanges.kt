package com.liamgleeson.vibeyfin.domain

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Says that what the user has watched may have changed: a play session ended (and the server has
 * been told, or has been tried), or a watched mark was written. Screens that show watch state
 * (Continue watching, an item's Resume button) reload on [events] instead of on a timer, so they
 * never read the server before the report that changes the answer has reached it.
 *
 * Nothing is replayed: a screen that starts loads on its own.
 */
class WatchChanges {
    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<Unit> = _events.asSharedFlow()

    fun changed() {
        _events.tryEmit(Unit)
    }
}
