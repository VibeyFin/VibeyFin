package app.vibeyfin.data.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Whether the server answers, as the app's own calls find it: a call that got nothing back (no connection, a timeout) counts as a
 * miss, and any answer, even an error status, as the server being there. The network can be up while the server is not (a LAN
 * server away from home, a server that is down), which the device's own network state cannot say. Only calls that carry a
 * token count; a probe of a guessed address is not the server the user is signed in to.
 */
class ServerReachability {
    private val misses = MutableStateFlow(0)

    /** Two misses in a row, with no answer between them: more than one stray timeout, and one retried read that failed. */
    val unreachable: Flow<Boolean> = misses.map { it >= MISSES_BEFORE_UNREACHABLE }.distinctUntilChanged()

    internal fun failed() = misses.update { it + 1 }

    internal fun answered() {
        misses.value = 0
    }

    private companion object {
        const val MISSES_BEFORE_UNREACHABLE = 2
    }
}
