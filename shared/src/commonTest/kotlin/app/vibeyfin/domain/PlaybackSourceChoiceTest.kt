package app.vibeyfin.domain

import app.vibeyfin.data.api.MediaSourceDto
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackSourceChoiceTest {
    private class Case(
        val name: String,
        val sources: List<MediaSourceDto>,
        val forceTranscode: Boolean,
        val expectedSourceId: String?,
        val expectedMethod: PlayMethod?,
    )

    private fun source(
        id: String,
        directPlay: Boolean = false,
        directStream: Boolean = false,
        transcodingUrl: String? = null,
    ): MediaSourceDto = MediaSourceDto(
        id = id,
        supportsDirectPlay = directPlay,
        supportsDirectStream = directStream,
        transcodingUrl = transcodingUrl,
    )

    private val cases: List<Case> = listOf(
        Case(
            name = "direct play",
            sources = listOf(source("a", directPlay = true)),
            forceTranscode = false,
            expectedSourceId = "a",
            expectedMethod = PlayMethod.DirectPlay,
        ),
        Case(
            name = "direct play beats an earlier transcode",
            sources = listOf(source("a", transcodingUrl = "/t"), source("b", directPlay = true)),
            forceTranscode = false,
            expectedSourceId = "b",
            expectedMethod = PlayMethod.DirectPlay,
        ),
        Case(
            name = "direct stream",
            sources = listOf(source("a", directStream = true, transcodingUrl = "/t")),
            forceTranscode = false,
            expectedSourceId = "a",
            expectedMethod = PlayMethod.DirectStream,
        ),
        Case(
            name = "transcode",
            sources = listOf(source("a", transcodingUrl = "/t")),
            forceTranscode = false,
            expectedSourceId = "a",
            expectedMethod = PlayMethod.Transcode,
        ),
        Case(
            name = "first transcode wins",
            sources = listOf(source("a", transcodingUrl = "/t1"), source("b", transcodingUrl = "/t2")),
            forceTranscode = false,
            expectedSourceId = "a",
            expectedMethod = PlayMethod.Transcode,
        ),
        Case(
            name = "force transcode skips direct play",
            sources = listOf(source("a", directPlay = true, directStream = true, transcodingUrl = "/t")),
            forceTranscode = true,
            expectedSourceId = "a",
            expectedMethod = PlayMethod.Transcode,
        ),
        Case(
            name = "force transcode without a transcode url",
            sources = listOf(source("a", directPlay = true)),
            forceTranscode = true,
            expectedSourceId = null,
            expectedMethod = null,
        ),
        Case(
            name = "no compatible stream",
            sources = listOf(source("a")),
            forceTranscode = false,
            expectedSourceId = null,
            expectedMethod = null,
        ),
        Case(
            name = "no sources",
            sources = emptyList(),
            forceTranscode = false,
            expectedSourceId = null,
            expectedMethod = null,
        ),
    )

    @Test
    fun `the source and method follow what the server offers`() {
        for (case in cases) {
            val choice = chooseSource(case.sources, case.forceTranscode)

            assertEquals(case.expectedSourceId, choice?.source?.id, case.name)
            assertEquals(case.expectedMethod, choice?.method, case.name)
        }
    }
}
