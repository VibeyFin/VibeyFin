package app.vibeyfin.ui.item

import app.vibeyfin.domain.DynamicRange
import app.vibeyfin.domain.StreamTrack
import app.vibeyfin.domain.VideoFormat
import app.vibeyfin.domain.model.ItemKind
import app.vibeyfin.domain.model.MediaItem
import app.vibeyfin.ui.common.Strings

/** The short facts under a title, in reading order: "S1 E3", year, age rating, runtime, rating. */
internal fun MediaItem.facts(): List<String> = listOfNotNull(
    episodeLabel(),
    year?.toString(),
    ageRating,
    // A show has seasons, not a runtime.
    if (kind == ItemKind.Series) seasonCount?.let(Strings::seasonCount) else runtimeMinutes?.let(::runtimeText),
    rating?.let(Strings::starRating),
)

/**
 * One line of the technical info: what it is, and what the file says. [reason] says why row [reasonRow] of [value] is a transcode.
 * [searchesSubtitles] puts "Search for subtitles" under the line's rows, and [deletesSubtitles] "Delete subtitles" after it.
 */
data class TechnicalLine(
    val label: String,
    val value: String,
    val reason: String? = null,
    val reasonRow: Int = 0,
    val searchesSubtitles: Boolean = false,
    val deletesSubtitles: Boolean = false,
) {
    val rows: List<TechnicalRow> get() = if (value.isEmpty()) emptyList() else value.split("\n").mapIndexed { index, row -> TechnicalRow(row, reason.takeIf { index == reasonRow }) }
}

/** One row of a technical line, and why it is a transcode when it is one. */
data class TechnicalRow(val text: String, val reason: String? = null)

/**
 * An external subtitle file the server can delete: its stream index on the item, a short name ("English · Forced") and the file's
 * own name, which tells two files in one language apart.
 */
data class DeletableSubtitle(val streamIndex: Int, val name: String, val fileName: String?) {
    /** What a screen reader says for the row that deletes it. */
    val description: String get() = Strings.deleteSubtitleDescription(name, fileName)
}

// Subtitles inside the video file can't be deleted, only the files beside it.
internal fun MediaItem.deletableSubtitles(): List<DeletableSubtitle> = subtitleTracks.mapNotNull { track ->
    val index = track.index?.takeIf { track.external } ?: return@mapNotNull null
    track.shortName()?.let { DeletableSubtitle(index, it, track.fileName) }
}

// The language and what kind of subtitle it is; the codec and "External" say nothing in a list of files.
private fun StreamTrack.shortName(): String? {
    val language = title ?: displayTitle?.substringBefore(" - ") ?: language ?: return null
    return listOfNotNull(language, Strings.trackForced.takeIf { forced }, Strings.trackSdh.takeIf { hearingImpaired }).joinToString(" · ")
}

/**
 * The file's video as one line ("3840×2160 · HEVC · HDR10 · 24 fps · 8.2 Mbps"), then how much storage it takes ("4.2 GB"); each
 * only when the server sent it.
 */
internal fun MediaItem.technicalLines(
    tags: PlayoutTags? = null,
    searchesSubtitles: Boolean = false,
    deletesSubtitles: Boolean = false,
    deletingSubtitle: Int? = null,
): List<TechnicalLine> = listOfNotNull(
    videoLine(tags?.video),
    audioLine(tags?.audio, tags?.audioTrack),
    subtitlesLine(searchesSubtitles, deletingSubtitle)?.let { it.copy(deletesSubtitles = deletesSubtitles && it.rows.isNotEmpty()) },
    fileBytes?.let { TechnicalLine(Strings.technicalSize, Strings.fileSize(it)) },
)

// A title with no such line, like a show, gets one with only how it plays.
private fun MediaItem.audioLine(tag: Playout?, track: Int?): TechnicalLine? {
    val named = audioTracks.mapNotNull { audio -> audio.name()?.let { audio to it } }
    if (named.isEmpty()) return tag?.let { TechnicalLine(Strings.technicalAudio, Strings.statsMethod(it.method), it.reason) }
    val row = named.indexOfFirst { it.first == track?.let(audioTracks::getOrNull) }.coerceAtLeast(0)
    val rows = named.mapIndexed { index, (audio, name) -> if (index == row) name.withPlayout(tag, audio.codec) else name }
    return TechnicalLine(Strings.technicalAudio, rows.joinToString("\n"), tag?.reason, row)
}

// Says how it plays right after the codec. A track's own name may call a codec something else ("Dolby Digital" for ac3): it then goes at the end of the row.
private fun String.withPlayout(tag: Playout?, codec: String?): String {
    if (tag == null) return this
    val at = codec?.let { indexOf(it, ignoreCase = true).takeIf { found -> found >= 0 }?.plus(it.length) } ?: length
    return take(at) + " " + Strings.playoutTag(tag.method) + drop(at)
}

// A title with no subtitle tracks still has the line when it offers the search: that is the case the search is for
private fun MediaItem.subtitlesLine(searches: Boolean, deleting: Int?): TechnicalLine? {
    val line = tracksLine(Strings.technicalSubtitles, subtitleTracks.filterNot { deleting != null && it.index == deleting })

    return if (searches) (line ?: TechnicalLine(Strings.technicalSubtitles, "")).copy(searchesSubtitles = true) else line
}

private fun tracksLine(label: String, tracks: List<StreamTrack>): TechnicalLine? {
    val names = tracks.mapNotNull { it.name() }
    return if (names.isEmpty()) null else TechnicalLine(label, names.joinToString("\n"))
}

private fun StreamTrack.name(): String? {
    displayTitle?.let { return it.replace(" - ", " · ") }
    val parts = listOfNotNull(
        title ?: language,
        codec?.uppercase(),
        Strings.trackForced.takeIf { forced },
        Strings.trackExternal.takeIf { external },
    )
    return if (parts.isEmpty()) null else parts.joinToString(" · ")
}

private fun MediaItem.videoLine(tag: Playout?): TechnicalLine? {
    val onlyTag = tag?.let { TechnicalLine(Strings.technicalVideo, Strings.statsMethod(it.method), it.reason) }
    val format = videoFormat ?: return onlyTag
    val parts = listOfNotNull(
        if (format.width != null && format.height != null) "${format.width}×${format.height}" else null,
        format.codec?.uppercase(),
        when (format.dynamicRange) {
            DynamicRange.DolbyVision -> Strings.badgeDolbyVision
            DynamicRange.Hdr10 -> Strings.badgeHdr10
            DynamicRange.Hlg -> Strings.badgeHlg
            DynamicRange.Sdr -> null
        },
        format.frameRate?.let { "${oneDecimal(it.toDouble()).removeSuffix(".0")} fps" },
        format.bitrateKbps?.let { "${oneDecimal(it / 1000.0)} Mbps" },
    )
    return if (parts.isEmpty()) onlyTag else TechnicalLine(Strings.technicalVideo, parts.joinToString(" · ").withPlayout(tag, format.codec), tag?.reason)
}

/** What a season's button says: "Season 3" whatever the server calls it ("Series 3", "Season 3"), and "Specials" for 0. */
val MediaItem.seasonLabel: String
    get() = when (val number = seasonIndex) {
        null -> title
        0 -> Strings.specials
        else -> Strings.season(number)
    }

/** On a season: its number, as the server calls it (0 is Specials). The server sends it in the field an episode uses for its own number. */
internal val MediaItem.seasonIndex: Int? get() = if (kind == ItemKind.Season) episodeNumber else null

/** The one badge for the picture: Dolby Vision, HDR10, HLG, then 4K, then HD; null when there is no video stream or it is smaller. */
internal fun MediaItem.formatBadge(): String? = videoFormat?.badge()

internal fun VideoFormat.badge(): String? = when {
    dynamicRange == DynamicRange.DolbyVision -> Strings.badgeDolbyVision
    dynamicRange == DynamicRange.Hdr10 -> Strings.badgeHdr10
    dynamicRange == DynamicRange.Hlg -> Strings.badgeHlg
    (width ?: 0) >= 3200 || (height ?: 0) >= 2000 -> Strings.badge4k
    (width ?: 0) >= 1200 || (height ?: 0) >= 700 -> Strings.badgeHd
    else -> null
}

internal fun MediaItem.episodeLabel(): String? {
    if (kind != ItemKind.Episode || episodeNumber == null) return null
    return seasonNumber?.let { "S$it E$episodeNumber" } ?: "E$episodeNumber"
}

internal fun runtimeText(minutes: Int): String {
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours == 0 -> "$rest min"
        rest == 0 -> "$hours h"
        else -> "$hours h $rest min"
    }
}

private fun oneDecimal(value: Double): String {
    val tenths = (value * 10).let { if (it >= 0) (it + 0.5).toInt() else (it - 0.5).toInt() }
    return "${tenths / 10}.${kotlin.math.abs(tenths % 10)}"
}
