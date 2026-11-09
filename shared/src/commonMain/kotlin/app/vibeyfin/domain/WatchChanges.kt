package app.vibeyfin.domain

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** [itemId] was put on My List ([favorite] true) or taken off it, and the server has saved it. */
data class FavoriteChange(val itemId: String, val favorite: Boolean)

/**
 * Says that what the user has watched may have changed: a play session ended (and the server has
 * been told, or has been tried), or a watched mark was written. Screens that show watch state
 * (Continue watching, an item's Resume button) reload on [events] instead of on a timer, so they
 * never read the server before the report that changes the answer has reached it. A title put on
 * My List or taken off it also says which, on [favorites].
 *
 * Nothing is replayed: a screen that starts loads on its own.
 */
class WatchChanges {
    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<Unit> = _events.asSharedFlow()

    private val _favorites = MutableSharedFlow<FavoriteChange>(extraBufferCapacity = FAVORITE_BUFFER, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val favorites: SharedFlow<FavoriteChange> = _favorites.asSharedFlow()

    fun changed() {
        _events.tryEmit(Unit)
    }

    fun favoriteChanged(change: FavoriteChange) {
        _favorites.tryEmit(change)
        changed()
    }
}

// Deep, because each change names a title and is not repeated by the next one.
private const val FAVORITE_BUFFER = 64
