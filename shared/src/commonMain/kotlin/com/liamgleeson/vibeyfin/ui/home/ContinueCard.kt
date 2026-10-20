package com.liamgleeson.vibeyfin.ui.home

import com.liamgleeson.vibeyfin.domain.model.ItemKind
import com.liamgleeson.vibeyfin.domain.model.MediaItem
import com.liamgleeson.vibeyfin.ui.common.Strings
import com.liamgleeson.vibeyfin.ui.item.episodeLabel

/** One card in the Continue watching row, ready to draw. Tapping it plays [id]. */
data class ContinueCard(
    val id: String,
    /** A movie's title, or the show's name for an episode. */
    val title: String,
    /** The episode ("S2 E5"); null for a movie. */
    val subtitle: String?,
    /** A 16:9 picture, or null when the server has none. */
    val imageUrl: String?,
    /** How far in the user is, from 0 to 1, for something in progress. Null for a Next Up card, which has no bar. */
    val progress: Float?,
    /** What a screen reader says: "{title}, {year or episode}, {n}% watched". */
    val accessibilityLabel: String,
)

/** The Continue watching row on Home. It is not drawn at all when there is nothing to show or say. */
data class ContinueRow(
    val cards: List<ContinueCard> = emptyList(),
    /** The first load is on its way and there is nothing to show yet: draw the skeleton. */
    val loading: Boolean = false,
    /** Why the row didn't load, with what to try. Only set when there are no cards to show instead. */
    val message: String? = null,
) {
    val isVisible: Boolean get() = loading || cards.isNotEmpty() || message != null
}

internal fun MediaItem.toContinueCard(): ContinueCard {
    val label = episodeLabel()
    val title = if (kind == ItemKind.Episode) seriesName ?: title else title
    val percent = progress?.let { (it * 100).toInt().coerceIn(1, 99) }
    return ContinueCard(
        id = id,
        title = title,
        subtitle = label,
        imageUrl = landscapeUrl,
        progress = progress,
        accessibilityLabel = Strings.continueCardLabel(title, label ?: year?.toString(), percent),
    )
}
