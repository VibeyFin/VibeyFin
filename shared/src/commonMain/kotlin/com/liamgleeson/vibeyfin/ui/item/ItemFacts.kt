package com.liamgleeson.vibeyfin.ui.item

import com.liamgleeson.vibeyfin.domain.model.ItemKind
import com.liamgleeson.vibeyfin.domain.model.MediaItem

/** The short facts under a title, in reading order: "S1 E3", year, age rating, runtime, rating. */
internal fun MediaItem.facts(): List<String> = listOfNotNull(
    episodeLabel(),
    year?.toString(),
    ageRating,
    runtimeMinutes?.let(::runtimeText),
    rating?.let { "★ ${oneDecimal(it)}" },
)

private fun MediaItem.episodeLabel(): String? {
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
