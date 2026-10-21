package app.vibeyfin.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Counts episodes that started by themselves since the viewer last did something.
 *
 * In memory and a Koin `single`, so it outlives the `PlaybackViewModel` that a new episode creates.
 * Safe to call from any thread: every change is an atomic [update].
 */
class BingeCounter {
    private val _count = MutableStateFlow(0)
    val count: StateFlow<Int> = _count.asStateFlow()

    fun autoAdvanced() = _count.update { it + 1 }

    fun interacted() = reset()

    fun reset() = _count.update { 0 }

    /** True once [threshold] auto advances happened. A [threshold] of 0 or less turns asking off. */
    fun shouldAsk(threshold: Int): Boolean = threshold > 0 && count.value >= threshold
}
