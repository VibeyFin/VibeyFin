package com.liamgleeson.vibeyfin.domain

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NowPlayingTest {
    private val episode = NowPlayingState("ep1", "Pilot", "Season 1", playing = true, positionMs = 1_000)

    @Test
    fun `clear removes state`() {
        val nowPlaying = NowPlaying()
        nowPlaying.update(episode)
        assertEquals(episode, nowPlaying.state.value)
        nowPlaying.clear()
        assertNull(nowPlaying.state.value)
    }

    @Test
    fun `requestRestore emits the current item id`() = runTest {
        val nowPlaying = NowPlaying()
        nowPlaying.update(episode)
        val received = async(start = CoroutineStart.UNDISPATCHED) { nowPlaying.restoreRequests.first() }
        nowPlaying.requestRestore()
        assertEquals("ep1", received.await())
    }

    @Test
    fun `requestRestore with nothing playing emits nothing`() = runTest {
        val nowPlaying = NowPlaying()
        val received = async(start = CoroutineStart.UNDISPATCHED) { nowPlaying.restoreRequests.first() }
        nowPlaying.requestRestore()
        assertEquals(false, received.isCompleted)
        received.cancel()
    }
}
