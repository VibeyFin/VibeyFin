package app.vibeyfin.ui.home

import app.vibeyfin.domain.model.ItemKind
import app.vibeyfin.domain.model.MediaItem
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.item.episodeLabel

/** One card in the Continue watching row, ready to draw. Tapping it plays [id]. */
data class ContinueCard(
    val id: String,
    /** A movie's title, or the show's name for an episode. */
    val title: String,
    /**
     * What is under the title: in progress, the time left ("38m left"), after the episode for an episode
     * ("S2 E5 · 38m left"); a Next Up card has the episode and its title ("S2 E5 · Episode title"). Null
     * for a movie nobody has started.
     */
    val subtitle: String?,
    /** A 16:9 picture, or null when the server has none. */
    val imageUrl: String?,
    /** The BlurHash of [imageUrl]'s picture, drawn while it loads; null when the server sent none. */
    val imageBlurHash: String?,
    /** How far in the user is, from 0 to 1, for something in progress. Null for a Next Up card, which has no bar. */
    val progress: Float?,
    /** What a screen reader says: "{title}, {year or episode}, {n}% watched". */
    val accessibilityLabel: String,
    /** An episode's show: "Remove" hides it from Next Up. */
    val seriesId: String? = null,
    /** Where the user stopped, 0 for a Next Up card: "Remove" clears it and "Undo" puts it back. */
    val resumePositionMs: Long = 0,
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

private const val SEPARATOR = " · "

internal fun MediaItem.toContinueCard(): ContinueCard {
    val isEpisode = kind == ItemKind.Episode
    val label = episodeLabel()
    val left = runtimeMs?.takeIf { progress != null }?.let { Strings.timeLeftShort(it - resumePositionMs) }
    val subtitle = when {
        // Started: how long is left, after the episode.
        left != null -> listOfNotNull(label.takeIf { isEpisode }, left).joinToString(SEPARATOR)

        // Next up: the episode and its title.
        isEpisode -> listOfNotNull(label, this.title.takeIf { it.isNotBlank() }).joinToString(SEPARATOR).ifEmpty { null }

        else -> null
    }
    val title = if (isEpisode) seriesName ?: title else title
    val percent = progress?.let { (it * 100).toInt().coerceIn(1, 99) }
    return ContinueCard(
        id = id,
        title = title,
        subtitle = subtitle,
        imageUrl = landscapeUrl,
        imageBlurHash = landscapeBlurHash,
        progress = progress,
        accessibilityLabel = Strings.continueCardLabel(title, label ?: year?.toString(), percent),
        seriesId = seriesId,
        resumePositionMs = resumePositionMs,
    )
}
