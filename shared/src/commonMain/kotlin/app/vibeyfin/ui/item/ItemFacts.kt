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
    runtimeMinutes?.let(::runtimeText),
    rating?.let { "★ ${oneDecimal(it)}" },
)

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
