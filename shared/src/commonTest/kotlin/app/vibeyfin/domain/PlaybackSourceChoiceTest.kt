package app.vibeyfin.domain

import app.vibeyfin.data.api.MediaSourceDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
        Case("direct play", listOf(source("a", directPlay = true)), false, "a", PlayMethod.DirectPlay),
        Case("direct play beats an earlier transcode", listOf(source("a", transcodingUrl = "/t"), source("b", directPlay = true)), false, "b", PlayMethod.DirectPlay),
        Case("direct stream", listOf(source("a", directStream = true, transcodingUrl = "/t")), false, "a", PlayMethod.DirectStream),
        Case("transcode", listOf(source("a", transcodingUrl = "/t")), false, "a", PlayMethod.Transcode),
        Case("first transcode wins", listOf(source("a", transcodingUrl = "/t1"), source("b", transcodingUrl = "/t2")), false, "a", PlayMethod.Transcode),
        Case("force transcode skips direct play", listOf(source("a", directPlay = true, directStream = true, transcodingUrl = "/t")), true, "a", PlayMethod.Transcode),
        Case("force transcode without a transcode url", listOf(source("a", directPlay = true)), true, null, null),
        Case("no compatible stream", listOf(source("a")), false, null, null),
        Case("no sources", emptyList(), false, null, null),
    )

    @Test
    fun `the source and method follow what the server offers`() {
        for (case in cases) {
            val choice = chooseSource(case.sources, case.forceTranscode)

            assertEquals(case.expectedSourceId, choice?.source?.id, case.name)
            assertEquals(case.expectedMethod, choice?.method, case.name)
            if (case.expectedSourceId == null) assertNull(choice, case.name)
        }
    }
}
