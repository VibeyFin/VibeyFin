package app.vibeyfin.ui.item

import app.vibeyfin.domain.DynamicRange
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

/** One line of the technical info: what it is, and what the file says. */
data class TechnicalLine(val label: String, val value: String)

/** The file's video as one line: "3840×2160 · HEVC · HDR10 · 24 fps · 8.2 Mbps"; null when the server sent no video stream. */
internal fun MediaItem.technicalLines(): List<TechnicalLine> {
    val format = videoFormat ?: return emptyList()
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
    return if (parts.isEmpty()) emptyList() else listOf(TechnicalLine(Strings.technicalVideo, parts.joinToString(" · ")))
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
