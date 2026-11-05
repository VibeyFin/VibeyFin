package app.vibeyfin.ui.item

import app.vibeyfin.domain.DynamicRange
import app.vibeyfin.domain.PlayMethod
import app.vibeyfin.domain.StreamTrack
import app.vibeyfin.domain.VideoFormat
import app.vibeyfin.domain.model.ItemKind
import app.vibeyfin.domain.model.MediaItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ItemFactsTest {
    private fun item(
        kind: ItemKind = ItemKind.Movie,
        year: Int? = null,
        runtimeMinutes: Int? = null,
        rating: Double? = null,
        ageRating: String? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
        videoFormat: VideoFormat? = null,
        fileBytes: Long? = null,
        audioTracks: List<StreamTrack> = emptyList(),
        subtitleTracks: List<StreamTrack> = emptyList(),
    ) = MediaItem(
        id = "i", title = "T", kind = kind, isFolder = false, year = year, runtimeMinutes = runtimeMinutes,
        genres = emptyList(), overview = null, rating = rating, ageRating = ageRating, seriesName = null,
        seasonNumber = seasonNumber, episodeNumber = episodeNumber, posterAspectRatio = 1f, posterUrl = null,
        backdropUrl = null, videoFormat = videoFormat, fileBytes = fileBytes, audioTracks = audioTracks, subtitleTracks = subtitleTracks,
    )

    private fun video(range: DynamicRange = DynamicRange.Sdr, width: Int = 1920, height: Int = 1080) = VideoFormat(codec = "hevc", width = width, height = height, dynamicRange = range, frameRate = 24f, bitrateKbps = null)

    @Test
    fun `Dolby Vision wins over resolution`() {
        assertEquals("Dolby Vision", item(videoFormat = video(DynamicRange.DolbyVision, 3840, 2160)).formatBadge())
    }

    @Test
    fun `HDR10 and HLG are named`() {
        assertEquals("HDR10", item(videoFormat = video(DynamicRange.Hdr10)).formatBadge())
        assertEquals("HLG", item(videoFormat = video(DynamicRange.Hlg, 3840, 2160)).formatBadge())
    }

    @Test
    fun `4K SDR shows 4K`() {
        assertEquals("4K", item(videoFormat = video(width = 3840, height = 2160)).formatBadge())
        assertEquals("4K", item(videoFormat = video(width = 3840, height = 1600)).formatBadge())
    }

    @Test
    fun `1080p and 720p show HD and smaller shows nothing`() {
        assertEquals("HD", item(videoFormat = video()).formatBadge())
        assertEquals("HD", item(videoFormat = video(width = 1280, height = 720)).formatBadge())
        assertNull(item(videoFormat = video(width = 720, height = 480)).formatBadge())
    }

    @Test
    fun `no video stream shows no badge`() {
        assertNull(item(videoFormat = null).formatBadge())
    }

    @Test
    fun `only one badge`() {
        // HDR 4K is a single string, not "4K HDR10".
        assertEquals("HDR10", item(videoFormat = video(DynamicRange.Hdr10, 3840, 2160)).formatBadge())
    }

    @Test
    fun aMovieShowsYearAgeRatingRuntimeAndScoreInThatOrder() {
        val facts = item(year = 2013, ageRating = "PG", runtimeMinutes = 84, rating = 7.3).facts()

        assertEquals(listOf("2013", "PG", "1 h 24 min", "★ 7.3"), facts)
    }

    @Test
    fun anEpisodeStartsWithItsSeasonAndNumber() {
        assertEquals(listOf("S2 E5", "2020"), item(ItemKind.Episode, year = 2020, seasonNumber = 2, episodeNumber = 5).facts())
        assertEquals(listOf("E5"), item(ItemKind.Episode, episodeNumber = 5).facts())
    }

    @Test
    fun onlyAnEpisodeHasAnEpisodeLabel() {
        assertEquals(emptyList(), item(ItemKind.Movie, seasonNumber = 1, episodeNumber = 2).facts())
    }

    @Test
    fun anItemWithNoFactsHasNone() {
        assertEquals(emptyList(), item().facts())
    }

    @Test
    fun runtimesReadLikeSpeech() {
        assertEquals("1 min", runtimeText(1))
        assertEquals("59 min", runtimeText(59))
        assertEquals("1 h", runtimeText(60))
        assertEquals("1 h 1 min", runtimeText(61))
        assertEquals("2 h 15 min", runtimeText(135))
    }

    @Test
    fun scoresShowOneDecimal() {
        assertEquals(listOf("★ 7.0"), item(rating = 7.0).facts())
        assertEquals(listOf("★ 7.3"), item(rating = 7.26).facts())
        assertEquals(listOf("★ 10.0"), item(rating = 10.0).facts())
        assertEquals(listOf("★ 0.5"), item(rating = 0.5).facts())
    }

    @Test
    fun `technical info says how much storage the file takes`() {
        assertEquals(
            listOf(TechnicalLine("Video", "1920×1080 · HEVC · 24 fps"), TechnicalLine("Size", "4.2 GB")),
            item(videoFormat = video(), fileBytes = 4_200_000_000).technicalLines(),
        )
        assertEquals(listOf(TechnicalLine("Size", "850 MB")), item(fileBytes = 850_000_000).technicalLines())
        assertEquals(emptyList(), item().technicalLines())
    }

    @Test
    fun `technical info lists the audio and subtitle tracks one per line between the video and the size`() {
        val lines = item(
            videoFormat = video(),
            fileBytes = 850_000_000,
            audioTracks = listOf(StreamTrack(displayTitle = "English - Dolby Digital - 5.1 - Default"), StreamTrack(displayTitle = "Italiano - AAC - Stereo")),
            subtitleTracks = listOf(
                StreamTrack(displayTitle = "English - SUBRIP - External"),
                StreamTrack(language = "fre", codec = "pgssub", forced = true),
            ),
        ).technicalLines()

        assertEquals(listOf("Video", "Audio", "Subtitles", "Size"), lines.map { it.label })
        assertEquals("English · Dolby Digital · 5.1 · Default\nItaliano · AAC · Stereo", lines[1].value)
        assertEquals("English · SUBRIP · External\nfre · PGSSUB · Forced", lines[2].value)
    }

    @Test
    fun `no subtitles leaves out the line`() {
        val lines = item(audioTracks = listOf(StreamTrack(language = "eng", codec = "aac"))).technicalLines()

        assertEquals(listOf(TechnicalLine("Audio", "eng · AAC")), lines)
    }

    @Test
    fun howItPlaysIsSaidAfterTheVideoCodec() {
        val format = VideoFormat(width = 1920, height = 1080, codec = "hevc", dynamicRange = DynamicRange.Sdr, frameRate = 24f, bitrateKbps = 8200)

        val video = item(videoFormat = format).technicalLines(PlayoutTags(video = Playout(PlayMethod.DirectPlay))).first()

        assertEquals(listOf(TechnicalRow("1920×1080 · HEVC (Direct play) · 24 fps · 8.2 Mbps")), video.rows)
    }

    @Test
    fun unsupportedAudioIsSaidAfterThePlayingTracksCodecWithWhyUnderIt() {
        val reason = "The audio format isn't supported"
        val tracks = listOf(StreamTrack(displayTitle = "English - AAC - Stereo", codec = "aac"), StreamTrack(displayTitle = "Italiano - DTS - 5.1", codec = "dts"))

        val audio = item(audioTracks = tracks).technicalLines(PlayoutTags(audio = Playout(PlayMethod.Transcode, reason), audioTrack = 1)).single()

        assertEquals(listOf(TechnicalRow("English · AAC · Stereo"), TechnicalRow("Italiano · DTS (Transcode) · 5.1", reason)), audio.rows)
    }

    @Test
    fun aCodecTheTrackNameCallsSomethingElseIsFollowedAtTheEndOfTheRow() {
        val audio = item(audioTracks = listOf(StreamTrack(displayTitle = "English - Dolby Digital - 5.1", codec = "ac3"))).technicalLines(PlayoutTags(audio = Playout(PlayMethod.Transcode))).single()

        assertEquals("English · Dolby Digital · 5.1 (Transcode)", audio.value)
    }

    @Test
    fun aTitleWithNoSuchLineGetsOneWithOnlyHowItPlays() {
        assertEquals(listOf(TechnicalLine("Video", "Direct play")), item().technicalLines(PlayoutTags(video = Playout(PlayMethod.DirectPlay))))
    }
}
