package com.liamgleeson.vibeyfin.domain

import com.liamgleeson.vibeyfin.domain.model.MediaItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * What plays after an item, and whether that happens on its own.
 *
 * This is the one seam towards Phase 2 (Next Up and auto-advance), which is built on another
 * branch. Until that lands, Koin binds [NoNextUpSource]. On rebase, replace that binding with
 * Phase 2's real implementation of this interface; nothing that consumes it should change.
 */
interface NextUpSource {
    /** True when the next item starts by itself at the end of the current one. */
    val autoAdvance: StateFlow<Boolean>

    /** Emits each time playback moved to the next item without the viewer asking. */
    val advanced: Flow<Unit>

    /** The item that follows [itemId], or null when nothing does. */
    suspend fun next(itemId: String): MediaItem?
}

/** Stand-in until Phase 2 lands: auto-advance is off and nothing ever follows. */
class NoNextUpSource : NextUpSource {
    override val autoAdvance: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()
    override val advanced: Flow<Unit> = emptyFlow()
    override suspend fun next(itemId: String): MediaItem? = null
}
