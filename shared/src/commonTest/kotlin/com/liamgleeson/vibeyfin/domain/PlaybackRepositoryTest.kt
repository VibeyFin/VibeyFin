package com.liamgleeson.vibeyfin.domain

import com.liamgleeson.vibeyfin.data.api.ApiError
import com.liamgleeson.vibeyfin.testing.HLS_PATH
import com.liamgleeson.vibeyfin.testing.Reply
import com.liamgleeson.vibeyfin.testing.TestApp
import com.liamgleeson.vibeyfin.testing.audioStreamJson
import com.liamgleeson.vibeyfin.testing.mediaSourceJson
import com.liamgleeson.vibeyfin.testing.playbackInfoJson
import com.liamgleeson.vibeyfin.testing.subtitleStreamJson
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

class PlaybackRepositoryTest {
    private val app = TestApp()

    @AfterTest
    fun tearDown() = runTest { app.close() }

    private suspend fun ready(forceTranscode: Boolean = false): PlaybackPlan {
        val negotiation = app.playback.negotiate("m1", forceTranscode)
        return (negotiation as? PlaybackNegotiation.Ready)?.plan ?: fail("Expected a plan, got $negotiation")
    }

    private suspend fun answerWith(json: String, allowTranscoding: Boolean = true) {
        app.signedIn(allowTranscoding = allowTranscoding).on("POST", "/Items/m1/PlaybackInfo", Reply.json(json))
    }

    private fun requestBody(): JsonObject =
        Json.parseToJsonElement(app.jellyfin.requestsTo("POST", "/Items/m1/PlaybackInfo").single().body).jsonObject

    @Test
    fun aFileTheDeviceCanPlayIsPlayedDirectly() = runTest {
        answerWith(playbackInfoJson(mediaSourceJson(container = "mp4")))

        val plan = ready()

        assertEquals(PlayMethod.DirectPlay, plan.method)
        val deviceId = app.preferences.deviceId()
        assertEquals(
            "http://nas:8096/Videos/m1/stream.mp4?static=true&mediaSourceId=ms-1&deviceId=$deviceId&playSessionId=ps-1&tag=etag-1",
            plan.url,
        )
        assertEquals("ms-1", plan.mediaSourceId)
        assertEquals("ps-1", plan.playSessionId)
        assertEquals(90 * 60_000L, plan.runtimeMs)
    }

    @Test
    fun theTokenTravelsInTheHeaderNeverInTheUrl() = runTest {
        answerWith(playbackInfoJson(mediaSourceJson()))

        val plan = ready()

        val authorization = plan.headers.getValue("Authorization")
        assertTrue(authorization.contains("Token=\"token-1\""), authorization)
        assertTrue(authorization.contains("Client=\"VibeyFin\""), authorization)
        assertTrue("token-1" !in plan.url, plan.url)
    }

    @Test
    fun aContainerListedFfprobeStyleUsesItsFirstName() = runTest {
        answerWith(playbackInfoJson(mediaSourceJson(container = "mov,mp4,m4a,3gp")))

        val url = ready().url

        assertTrue(url.contains("/Videos/m1/stream.mov?"), url)
    }

    @Test
    fun aSourceWithNoContainerStillGetsAStreamUrl() = runTest {
        answerWith(playbackInfoJson(mediaSourceJson(container = null, etag = null)))

        val url = ready().url

        assertTrue(url.contains("/Videos/m1/stream?static=true"), url)
        assertTrue("tag=" !in url, url)
    }

    @Test
    fun aRemuxIsDirectStreamedFromTheServersUrl() = runTest {
        answerWith(
            playbackInfoJson(mediaSourceJson(container = "mkv", directPlay = false, directStream = true, transcodingUrl = HLS_PATH)),
        )

        val plan = ready()

        assertEquals(PlayMethod.DirectStream, plan.method)
        assertEquals("http://nas:8096$HLS_PATH", plan.url)
    }

    @Test
    fun aFileTheDeviceCantPlayIsTranscoded() = runTest {
        answerWith(
            playbackInfoJson(mediaSourceJson(container = "mkv", directPlay = false, directStream = false, transcodingUrl = HLS_PATH)),
        )

        val plan = ready()

        assertEquals(PlayMethod.Transcode, plan.method)
        assertEquals("http://nas:8096$HLS_PATH", plan.url)
    }

    @Test
    fun anAbsoluteTranscodeUrlIsLeftAlone() = runTest {
        answerWith(
            playbackInfoJson(
                mediaSourceJson(directPlay = false, directStream = false, transcodingUrl = "https://cdn.example.com/master.m3u8?x=1"),
            ),
        )

        assertEquals("https://cdn.example.com/master.m3u8?x=1", ready().url)
    }

    @Test
    fun theFirstSourceThatCanBePlayedWins() = runTest {
        answerWith(
            playbackInfoJson(
                mediaSourceJson(id = "ms-4k", directPlay = false, directStream = false, transcodingUrl = HLS_PATH),
                mediaSourceJson(id = "ms-hd", container = "mp4"),
            ),
        )

        val plan = ready()

        assertEquals("ms-hd", plan.mediaSourceId)
        assertEquals(PlayMethod.DirectPlay, plan.method)
    }

    @Test
    fun withNothingTheServerCanDeliverThereIsNoCompatibleStream() = runTest {
        answerWith(playbackInfoJson(mediaSourceJson(directPlay = false, directStream = false, transcodingUrl = null), errorCode = "NoCompatibleStream"))

        assertEquals(PlaybackNegotiation.NoCompatibleStream, app.playback.negotiate("m1"))
    }

    @Test
    fun anAnswerWithNoSourcesAtAllIsNoCompatibleStream() = runTest {
        answerWith(playbackInfoJson())

        assertEquals(PlaybackNegotiation.NoCompatibleStream, app.playback.negotiate("m1"))
    }

    @Test
    fun forcingATranscodeTurnsDirectPlayOffAndTakesTheTranscode() = runTest {
        // A server that offers both: a forced transcode must still not take the direct source.
        answerWith(
            playbackInfoJson(mediaSourceJson(directPlay = true, directStream = true, transcodingUrl = HLS_PATH)),
        )

        val plan = ready(forceTranscode = true)

        assertEquals(PlayMethod.Transcode, plan.method)
        assertEquals("http://nas:8096$HLS_PATH", plan.url)
        val body = requestBody()
        assertEquals(false, body.getValue("EnableDirectPlay").jsonPrimitive.boolean)
        assertEquals(false, body.getValue("EnableDirectStream").jsonPrimitive.boolean)
        assertEquals(true, body.getValue("EnableTranscoding").jsonPrimitive.boolean)
    }

    @Test
    fun withTranscodingOffAFileThatPlaysAsItIsStillPlays() = runTest {
        answerWith(playbackInfoJson(mediaSourceJson(container = "mp4", transcodingUrl = HLS_PATH)), allowTranscoding = false)

        assertEquals(PlayMethod.DirectPlay, ready().method)
    }

    @Test
    fun withTranscodingOffARemuxOrAnAudioConversionIsStillTaken() = runTest {
        val reasons = "&TranscodeReasons=ContainerNotSupported,AudioCodecNotSupported"
        answerWith(
            playbackInfoJson(mediaSourceJson(container = "mkv", directPlay = false, directStream = true, transcodingUrl = HLS_PATH + reasons)),
            allowTranscoding = false,
        )

        val plan = ready()

        assertEquals(PlayMethod.DirectStream, plan.method)
        assertEquals("http://nas:8096$HLS_PATH$reasons", plan.url)
    }

    @Test
    fun withTranscodingOffAStreamThatWouldReencodeTheVideoNeedsTranscoding() = runTest {
        answerWith(
            playbackInfoJson(
                mediaSourceJson(
                    container = "mkv", directPlay = false, directStream = false,
                    transcodingUrl = "$HLS_PATH&TranscodeReasons=ContainerNotSupported,VideoCodecNotSupported",
                ),
            ),
            allowTranscoding = false,
        )

        assertEquals(PlaybackNegotiation.NeedsTranscoding, app.playback.negotiate("m1"))
    }

    @Test
    fun withTranscodingOffASubtitleBurnedInCountsAsReencodingTheVideo() = runTest {
        answerWith(
            playbackInfoJson(mediaSourceJson(directPlay = false, directStream = false, transcodingUrl = "$HLS_PATH&TranscodeReasons=SubtitleCodecNotSupported")),
            allowTranscoding = false,
        )

        assertEquals(PlaybackNegotiation.NeedsTranscoding, app.playback.negotiate("m1"))
    }

    @Test
    fun withTranscodingOnAVideoReencodeIsTaken() = runTest {
        answerWith(
            playbackInfoJson(
                mediaSourceJson(directPlay = false, directStream = false, transcodingUrl = "$HLS_PATH&TranscodeReasons=VideoCodecNotSupported"),
            ),
        )

        assertEquals(PlayMethod.Transcode, ready().method)
    }

    @Test
    fun withTranscodingOffNothingTheServerCanDeliverIsStillNoCompatibleStream() = runTest {
        answerWith(playbackInfoJson(mediaSourceJson(directPlay = false, directStream = false, transcodingUrl = null)), allowTranscoding = false)

        assertEquals(PlaybackNegotiation.NoCompatibleStream, app.playback.negotiate("m1"))
    }

    @Test
    fun theTranscodeProfileCarriesTheVideoCodecsThePlayerTakesInHls() = runTest {
        app.capabilities.capabilities = app.capabilities.current().copy(streamVideoCodecs = listOf("h264", "hevc"))
        answerWith(playbackInfoJson(mediaSourceJson()))
        ready()

        val profile = requestBody().getValue("DeviceProfile").jsonObject
        val transcoding = profile.getValue("TranscodingProfiles").jsonArray.single().jsonObject
        assertEquals("h264,hevc", transcoding.getValue("VideoCodec").jsonPrimitive.content)
    }

    @Test
    fun theRequestCarriesTheDeviceProfileAndLeavesTheTracksToTheServer() = runTest {
        answerWith(playbackInfoJson(mediaSourceJson()))

        ready()

        val request = app.jellyfin.requestsTo("POST", "/Items/m1/PlaybackInfo").single()
        assertEquals("u1", request.query["userId"])
        val body = requestBody()
        assertEquals("u1", body.getValue("UserId").jsonPrimitive.content)
        assertEquals(MAX_STREAMING_BITRATE, body.getValue("MaxStreamingBitrate").jsonPrimitive.int)
        assertEquals(true, body.getValue("EnableDirectPlay").jsonPrimitive.boolean)
        // No choice has been made, so the server picks the tracks by the user's settings.
        assertTrue("MediaSourceId" !in body && "AudioStreamIndex" !in body && "SubtitleStreamIndex" !in body, body.toString())

        val profile = body.getValue("DeviceProfile").jsonObject
        val directPlay = profile.getValue("DirectPlayProfiles").jsonArray.single().jsonObject
        assertEquals("mp4,mkv", directPlay.getValue("Container").jsonPrimitive.content)
        assertEquals("h264", directPlay.getValue("VideoCodec").jsonPrimitive.content)
        assertEquals("aac", directPlay.getValue("AudioCodec").jsonPrimitive.content)
        val transcoding = profile.getValue("TranscodingProfiles").jsonArray.single().jsonObject
        assertEquals("hls", transcoding.getValue("Protocol").jsonPrimitive.content)
        assertEquals("ts", transcoding.getValue("Container").jsonPrimitive.content)
        assertEquals("h264", transcoding.getValue("VideoCodec").jsonPrimitive.content)
        assertEquals("aac", transcoding.getValue("AudioCodec").jsonPrimitive.content)
    }

    @Test
    fun whatTheDeviceCanDecodeDecidesTheDirectPlayProfile() = runTest {
        answerWith(playbackInfoJson(mediaSourceJson()))
        app.capabilities.capabilities = PlaybackCapabilities(
            containers = listOf("mp4", "m4v", "mov"),
            videoCodecs = listOf("h264", "hevc"),
            audioCodecs = listOf("aac", "ac3"),
        )

        ready()

        val profile = requestBody().getValue("DeviceProfile").jsonObject
        val directPlay = profile.getValue("DirectPlayProfiles").jsonArray.single().jsonObject
        assertEquals("mp4,m4v,mov", directPlay.getValue("Container").jsonPrimitive.content)
        assertEquals("h264,hevc", directPlay.getValue("VideoCodec").jsonPrimitive.content)
        assertEquals("aac,ac3", directPlay.getValue("AudioCodec").jsonPrimitive.content)
    }

    @Test
    fun tenBitH264AndVeryHighLevelsAreLeftToTheServerToTranscode() = runTest {
        answerWith(playbackInfoJson(mediaSourceJson()))

        ready()

        val profiles = requestBody().getValue("DeviceProfile").jsonObject.getValue("CodecProfiles").jsonArray
        val h264 = profiles.map { it.jsonObject }.single { it.getValue("Codec").jsonPrimitive.content == "h264" }
        val conditions = (h264.getValue("Conditions") as JsonArray).map { it.jsonObject }
        fun condition(property: String) = conditions.single { it.getValue("Property").jsonPrimitive.content == property }
        assertEquals("8", condition("VideoBitDepth").getValue("Value").jsonPrimitive.content)
        assertEquals("52", condition("VideoLevel").getValue("Value").jsonPrimitive.content)
        assertEquals("LessThanEqual", condition("VideoBitDepth").getValue("Condition").jsonPrimitive.content)
    }

    @Test
    fun anAnswerWithoutAPlaySessionIsUnusable() = runTest {
        answerWith(playbackInfoJson(mediaSourceJson(), playSessionId = null))

        assertEquals(PlaybackNegotiation.Failed(ApiError.BadResponse), app.playback.negotiate("m1"))
    }

    @Test
    fun serverErrorsComeBackAsTypedFailures() = runTest {
        val server = app.signedIn()
        server.on("POST", "/Items/gone/PlaybackInfo", Reply.status(HttpStatusCode.NotFound))
        server.on("POST", "/Items/denied/PlaybackInfo", Reply.status(HttpStatusCode.Forbidden))

        assertEquals(PlaybackNegotiation.Failed(ApiError.NotFound), app.playback.negotiate("gone"))
        assertEquals(PlaybackNegotiation.Failed(ApiError.Forbidden), app.playback.negotiate("denied"))
    }

    @Test
    fun aRejectedTokenIsUnauthorizedAndNegotiationIsNotRepeated() = runTest {
        val server = app.signedIn()
        server.on("POST", "/Items/m1/PlaybackInfo", Reply.status(HttpStatusCode.Unauthorized))

        val result = app.playback.negotiate("m1")

        assertEquals(PlaybackNegotiation.Failed(ApiError.Unauthorized), result)
        // It opens a play session on the server, so it is never sent twice by itself.
        assertEquals(1, app.jellyfin.requestsTo("POST", "/Items/m1/PlaybackInfo").size)
    }

    @Test
    fun withNobodySignedInTheAnswerIsUnauthorized() = runTest {
        val result = app.playback.negotiate("m1")

        assertIs<PlaybackNegotiation.Failed>(result)
        assertEquals(ApiError.Unauthorized, result.error)
        assertTrue(app.jellyfin.requests.isEmpty())
    }

    // -- audio and subtitle tracks --

    private val twoAudioTwoSubtitles = listOf(
        audioStreamJson(1, displayTitle = "English - AAC - Stereo - Default", language = "eng"),
        audioStreamJson(2, displayTitle = "Spanish - AAC - Stereo", language = "spa"),
        subtitleStreamJson(3, displayTitle = "English - SubRip", language = "eng"),
        subtitleStreamJson(4, displayTitle = "Spanish - SubRip", language = "spa"),
    )

    @Test
    fun theTracksAreListedWithTheServersNamesAndItsOwnPick() = runTest {
        answerWith(playbackInfoJson(mediaSourceJson(streams = twoAudioTwoSubtitles, defaultAudio = 2, defaultSubtitle = 3)))

        val plan = ready()

        assertEquals(
            listOf(
                PlaybackTrack(1, "English - AAC - Stereo - Default", selected = false, codec = "aac"),
                PlaybackTrack(2, "Spanish - AAC - Stereo", selected = true, codec = "aac"),
            ),
            plan.audioTracks,
        )
        assertEquals(
            listOf(PlaybackTrack(3, "English - SubRip", selected = true), PlaybackTrack(4, "Spanish - SubRip", selected = false)),
            plan.subtitleTracks,
        )
        assertEquals(2, plan.audioStreamIndex)
        assertEquals(3, plan.subtitleStreamIndex)
    }

    @Test
    fun withNoDefaultAudioTheFirstTrackIsSelectedAndSubtitlesAreOff() = runTest {
        answerWith(playbackInfoJson(mediaSourceJson(streams = twoAudioTwoSubtitles, defaultAudio = null, defaultSubtitle = -1)))

        val plan = ready()

        assertEquals(1, plan.audioStreamIndex)
        assertEquals(null, plan.subtitleStreamIndex)
        assertTrue(plan.subtitleTracks.none { it.selected })
    }

    @Test
    fun aTrackWithoutAServerNameIsNamedFromWhatTheFileSays() = runTest {
        answerWith(
            playbackInfoJson(
                mediaSourceJson(
                    streams = listOf(
                        audioStreamJson(1, language = "fra", codec = "ac3"),
                        audioStreamJson(2, codec = "aac"),
                    ),
                ),
            ),
        )

        assertEquals(listOf("fra · AC3", "AAC"), ready().audioTracks.map { it.name })
    }

    @Test
    fun aTrackThatSaysNothingAboutItselfHasNoName() = runTest {
        answerWith(playbackInfoJson(mediaSourceJson(streams = listOf("""{"Index":1,"Type":"Audio"}"""))))

        assertEquals(listOf(null), ready().audioTracks.map { it.name })
    }

    @Test
    fun everySubtitleTheServerSendsAsAFileIsListedForTheStreamToCarry() = runTest {
        answerWith(
            playbackInfoJson(
                mediaSourceJson(
                    streams = listOf(
                        subtitleStreamJson(3, displayTitle = "English", language = "eng", url = "/Videos/m1/ms-1/Subtitles/3/0/Stream.srt?ApiKey=k"),
                        subtitleStreamJson(4, language = "spa", url = "/Videos/m1/ms-1/Subtitles/4/0/Stream.vtt"),
                        subtitleStreamJson(5, url = "/Videos/m1/ms-1/Subtitles/5/0/Stream.ass"),
                    ),
                ),
            ),
        )

        val files = ready().subtitleFiles

        assertEquals(listOf(3, 4, 5), files.map { it.index })
        assertEquals(
            listOf("http://nas:8096/Videos/m1/ms-1/Subtitles/3/0/Stream.srt?ApiKey=k", "http://nas:8096/Videos/m1/ms-1/Subtitles/4/0/Stream.vtt", "http://nas:8096/Videos/m1/ms-1/Subtitles/5/0/Stream.ass"),
            files.map { it.url },
        )
        assertEquals(listOf("application/x-subrip", "text/vtt", "text/x-ssa"), files.map { it.mimeType })
        assertEquals(listOf("eng", "spa", null), files.map { it.language })
        assertEquals("English", files.first().label)
    }

    @Test
    fun aSubtitleTheServerWouldBurnInIsNotAFileAndAFormatWeCantNameIsLeftOut() = runTest {
        answerWith(
            playbackInfoJson(
                mediaSourceJson(
                    streams = listOf(
                        subtitleStreamJson(3, delivery = "Encode", codec = "hdmv_pgs_subtitle"),
                        subtitleStreamJson(4, url = "/Videos/m1/ms-1/Subtitles/4/0/Stream.sup"),
                        subtitleStreamJson(5, url = "/Videos/m1/ms-1/Subtitles/5/0/Stream.srt"),
                    ),
                ),
            ),
        )

        assertEquals(listOf(5), ready().subtitleFiles.map { it.index })
    }

    @Test
    fun aSelectedSubtitleTheServerBurnsInIsFlaggedAsSuch() = runTest {
        val streams = listOf(subtitleStreamJson(3, delivery = "Encode"), subtitleStreamJson(4))
        answerWith(playbackInfoJson(mediaSourceJson(streams = streams, defaultSubtitle = 3)))

        assertTrue(ready().subtitleBurnedIn)
    }

    @Test
    fun aSelectedSubtitleSentAsAFileOrNoSubtitleIsNotBurnedIn() = runTest {
        val streams = listOf(subtitleStreamJson(3, delivery = "Encode"), subtitleStreamJson(4))
        answerWith(playbackInfoJson(mediaSourceJson(streams = streams, defaultSubtitle = 4)))
        assertEquals(false, ready().subtitleBurnedIn)

        // Burn-in is a property of the selected subtitle; an Encode track that isn't shown costs nothing.
        answerWith(playbackInfoJson(mediaSourceJson(streams = streams, defaultSubtitle = -1)))
        assertEquals(false, ready().subtitleBurnedIn)
    }

    @Test
    fun theUsersChoicesAreSentWithTheSourceTheyAreFor() = runTest {
        answerWith(playbackInfoJson(mediaSourceJson(streams = twoAudioTwoSubtitles)))

        app.playback.negotiate("m1", mediaSourceId = "ms-1", audioStreamIndex = 2, subtitleStreamIndex = 4)

        val body = requestBody()
        assertEquals("ms-1", body.getValue("MediaSourceId").jsonPrimitive.content)
        assertEquals(2, body.getValue("AudioStreamIndex").jsonPrimitive.int)
        assertEquals(4, body.getValue("SubtitleStreamIndex").jsonPrimitive.int)
    }

    @Test
    fun noSubtitlesIsSentAsMinusOne() = runTest {
        answerWith(playbackInfoJson(mediaSourceJson(streams = twoAudioTwoSubtitles)))

        app.playback.negotiate("m1", mediaSourceId = "ms-1", audioStreamIndex = 1, subtitleStreamIndex = PlaybackRepository.SUBTITLES_OFF)

        assertEquals(-1, requestBody().getValue("SubtitleStreamIndex").jsonPrimitive.int)
    }

    @Test
    fun choicesWithoutASourceAreNotSentBecauseTheServerWouldIgnoreThem() = runTest {
        // Jellyfin applies AudioStreamIndex and SubtitleStreamIndex only when the request names the source.
        answerWith(playbackInfoJson(mediaSourceJson(streams = twoAudioTwoSubtitles)))

        app.playback.negotiate("m1", mediaSourceId = null, audioStreamIndex = 2, subtitleStreamIndex = 4)

        val body = requestBody()
        assertTrue("AudioStreamIndex" !in body && "SubtitleStreamIndex" !in body && "MediaSourceId" !in body, body.toString())
    }

    @Test
    fun theSubtitleFormatsTheDeviceLoadsBecomeExternalSubtitleProfiles() = runTest {
        answerWith(playbackInfoJson(mediaSourceJson()))
        app.capabilities.capabilities = app.capabilities.capabilities.copy(subtitleFormats = listOf("srt", "vtt"))

        ready()

        val profiles = requestBody().getValue("DeviceProfile").jsonObject.getValue("SubtitleProfiles").jsonArray.map { it.jsonObject }
        assertEquals(listOf("srt", "vtt"), profiles.map { it.getValue("Format").jsonPrimitive.content })
        assertEquals(listOf("External", "External"), profiles.map { it.getValue("Method").jsonPrimitive.content })
    }

    @Test
    fun aDeviceThatLoadsNoSubtitleFilesSendsNoSubtitleProfiles() = runTest {
        answerWith(playbackInfoJson(mediaSourceJson()))

        ready()

        assertTrue("SubtitleProfiles" !in requestBody().getValue("DeviceProfile").jsonObject)
    }

    private suspend fun formatOf(rangeType: String?): VideoFormat =
        answerWith(playbackInfoJson(mediaSourceJson(videoWidth = 3840, videoHeight = 2160, videoRangeType = rangeType)))
            .let { ready().videoFormat ?: fail("Expected a video format") }

    @Test
    fun videoFormatReadsVideoRangeType() = runTest {
        val format = formatOf("HDR10")

        assertEquals(DynamicRange.Hdr10, format.dynamicRange)
        assertEquals("h264", format.codec)
        assertEquals(3840, format.width)
        assertEquals(2160, format.height)
    }

    @Test
    fun videoFormatMapsEveryRangeType() = runTest {
        val expected = mapOf(
            "HDR10" to DynamicRange.Hdr10,
            "HDR10Plus" to DynamicRange.Hdr10,
            "DOVI" to DynamicRange.DolbyVision,
            "DOVIWithHDR10" to DynamicRange.DolbyVision,
            "HLG" to DynamicRange.Hlg,
            "SDR" to DynamicRange.Sdr,
            "Unknown" to DynamicRange.Sdr,
        )
        for ((wire, range) in expected) {
            assertEquals(range, formatOf(wire).dynamicRange, wire)
        }
    }

    @Test
    fun videoFormatIsSdrWhenTheServerGivesNoRangeType() = runTest {
        assertEquals(DynamicRange.Sdr, formatOf(null).dynamicRange)
    }

    private fun segmentsJson(vararg items: String) =
        """{"Items":[${items.joinToString(",")}],"TotalRecordCount":${items.size},"StartIndex":0}"""

    private fun segmentJson(type: String, startTicks: Long, endTicks: Long) =
        """{"Id":"s-$type","ItemId":"m1","Type":"$type","StartTicks":$startTicks,"EndTicks":$endTicks}"""

    @Test
    fun segmentsAreMappedFromTicks() = runTest {
        app.signedIn().on(
            "GET",
            "/MediaSegments/m1",
            Reply.json(
                segmentsJson(
                    segmentJson("Intro", 10_000_000L, 905_000_000L),
                    segmentJson("Outro", 54_000_000_000L, 60_000_000_000L),
                    segmentJson("Mystery", 0L, 10_000_000L),
                ),
            ),
        )

        val segments = app.playback.segments("m1")

        assertEquals(
            listOf(
                MediaSegment(SegmentType.Intro, 1_000L, 90_500L),
                MediaSegment(SegmentType.Outro, 5_400_000L, 6_000_000L),
                MediaSegment(SegmentType.Unknown, 0L, 1_000L),
            ),
            segments,
        )
    }

    @Test
    fun segmentsAreEmptyWhenTheServerAnswers404() = runTest {
        app.signedIn().on("GET", "/MediaSegments/m1", Reply.status(HttpStatusCode.NotFound))

        assertEquals(emptyList(), app.playback.segments("m1"))
    }

    private fun trickplayItemJson(trickplay: String) =
        """{"Items":[{"Id":"m1","Name":"Film","Type":"Movie","Trickplay":$trickplay}],"TotalRecordCount":1,"StartIndex":0}"""

    private fun infoJson(width: Int) =
        """"$width":{"Width":$width,"Height":${width * 9 / 16},"TileWidth":10,"TileHeight":10,"ThumbnailCount":250,"Interval":10000,"Bandwidth":1}"""

    @Test
    fun trickplayPicksTheWidthClosestTo320() = runTest {
        app.signedIn().on(
            "GET",
            "/Items",
            Reply.json(trickplayItemJson("""{"ms-1":{${infoJson(160)},${infoJson(400)},${infoJson(640)}},"ms-2":{${infoJson(320)}}}""")),
        )

        val track = app.playback.trickplay("m1", "ms-1") ?: fail("Expected a track")

        assertEquals(400, track.width)
        assertEquals(225, track.thumbHeight)
        assertEquals("ms-1", track.mediaSourceId)
        assertEquals(10_000L, track.intervalMs)
    }

    @Test
    fun trickplayIsNullWhenTheSourceHasNone() = runTest {
        app.signedIn().on("GET", "/Items", Reply.json(trickplayItemJson("""{"ms-2":{${infoJson(320)}}}""")))

        assertEquals(null, app.playback.trickplay("m1", "ms-1"))
    }
}
