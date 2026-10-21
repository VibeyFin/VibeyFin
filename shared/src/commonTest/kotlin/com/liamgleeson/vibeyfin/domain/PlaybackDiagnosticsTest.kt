package com.liamgleeson.vibeyfin.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaybackDiagnosticsTest {
    private val plan = PlaybackDiagnostics(
        method = PlayMethod.DirectPlay,
        transcodeReasons = emptyList(),
        videoCodec = "h264",
        audioCodec = "aac",
        width = 1920,
        height = 1080,
        frameRate = 24f,
        bitrateKbps = 8000,
        droppedFrames = null,
        throughputKbps = null,
        bufferMs = null,
    )

    private val nothing = LiveDiagnostics(null, null, null, null, null, null, null, null, null)

    @Test
    fun `live values win over the plan's`() {
        val merged = plan.withLive(nothing.copy(videoCodec = "hevc", width = 3840, height = 2160, droppedFrames = 3, bufferMs = 12_000))

        assertEquals("hevc", merged.videoCodec)
        assertEquals(3840, merged.width)
        assertEquals(2160, merged.height)
        assertEquals(3, merged.droppedFrames)
        assertEquals(12_000L, merged.bufferMs)
    }

    @Test
    fun `null live values never erase the plan's`() {
        assertEquals(plan, plan.withLive(nothing))
    }

    @Test
    fun `merging keeps the method and reasons`() {
        val transcode = plan.copy(method = PlayMethod.Transcode, transcodeReasons = listOf("VideoCodecNotSupported"))

        val merged = transcode.withLive(nothing.copy(bitrateKbps = 2000))

        assertEquals(PlayMethod.Transcode, merged.method)
        assertEquals(listOf("VideoCodecNotSupported"), merged.transcodeReasons)
        assertEquals(2000, merged.bitrateKbps)
    }

    @Test
    fun `reasons are read from the query`() {
        assertEquals(
            listOf("VideoCodecNotSupported", "AudioCodecNotSupported"),
            transcodeReasons("http://nas/master.m3u8?a=1&TranscodeReasons=VideoCodecNotSupported,AudioCodecNotSupported&b=2"),
        )
    }

    @Test
    fun `the reasons parameter is matched without regard to case`() {
        assertEquals(listOf("ContainerNotSupported"), transcodeReasons("http://nas/x?transcodereasons=ContainerNotSupported"))
    }

    @Test
    fun `a url without reasons has none`() {
        assertEquals(emptyList(), transcodeReasons("http://nas/master.m3u8?a=1"))
        assertEquals(emptyList(), transcodeReasons("http://nas/master.m3u8"))
        assertEquals(emptyList(), transcodeReasons("http://nas/x?TranscodeReasons="))
    }

    @Test
    fun `an unset field stays null`() {
        assertNull(plan.withLive(nothing).droppedFrames)
    }
}
