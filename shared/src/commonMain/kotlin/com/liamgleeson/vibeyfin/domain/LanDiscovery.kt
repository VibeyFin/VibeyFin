package com.liamgleeson.vibeyfin.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** A server that answered a broadcast. It is unverified: it still has to pass the Add server check. */
data class DiscoveredServer(
    val id: String,
    val name: String,
    /** Where the server says it can be reached, e.g. `http://192.168.1.20:8096`. */
    val address: String,
)

interface LanDiscovery {
    /** False where the platform can't broadcast, so the screen hides the search instead of showing nothing. */
    val isSupported: Boolean

    /** Asks the local network who is out there. Emits each server once as it answers, then completes. */
    fun discover(): Flow<DiscoveredServer>
}

/** For platforms that can't broadcast yet. iOS needs an Apple-approved multicast entitlement. */
object NoLanDiscovery : LanDiscovery {
    override val isSupported = false

    override fun discover(): Flow<DiscoveredServer> = emptyFlow()
}
