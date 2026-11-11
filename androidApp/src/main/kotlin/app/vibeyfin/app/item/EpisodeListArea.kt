package app.vibeyfin.app.item

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.vibeyfin.app.components.LoadError
import app.vibeyfin.app.components.ROW_FADE_MS
import app.vibeyfin.app.components.bleed
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.ui.common.Strings

/** One season button: what it selects by and what it says. */
internal class SeasonChip<K : Any>(val id: K, val label: String)

/**
 * A show's seasons as a row of buttons. The row opens
 * scrolled to the selected season.
 */
@Composable
internal fun <K : Any> SeasonChipsRow(seasons: List<SeasonChip<K>>, selectedId: K?, margin: Dp, onSelect: (K) -> Unit) {
    val listState = rememberLazyListState()
    val opening = seasons.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)

    LaunchedEffect(seasons.size) { if (opening > 0) listState.scrollToItem(opening) }
    LazyRow(
        state = listState,
        modifier = Modifier.bleed(margin).semantics { contentDescription = Strings.seasonsDescription(seasons.size) },
        contentPadding = PaddingValues(horizontal = margin),
        horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2),
    ) {
        items(seasons, key = { it.id }) { season ->
            val selected = season.id == selectedId

            FilterChip(
                selected = selected,
                onClick = { onSelect(season.id) },
                label = { Text(season.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = VibeyFinTokens.Colors.surface2,
                    labelColor = VibeyFinTokens.Colors.inkMuted,
                    selectedContainerColor = VibeyFinTokens.Colors.sparkSoft,
                    selectedLabelColor = VibeyFinTokens.Colors.onSparkSoft,
                ),
                border = null,
                modifier = Modifier.semantics { contentDescription = Strings.seasonDescription(season.label, selected) },
            )
        }
    }
}

/** What an episode area has to draw: the episodes, whether more are on their way, and why they didn't come. */
internal class EpisodeSnapshot<E>(val items: List<E>, val loading: Boolean, val message: String?)

/**
 * What the episode area shows: loading cards, a failure, or the list. Two phases are equal when they show the same kind of
 * thing for the same season, so the area only animates on a real change; [snapshot] is what the phase showed when it was made,
 * kept so that a phase fading out keeps drawing its own content, not the next one's.
 */
private class EpisodePhase<E>(val seasonKey: Any?, val index: Int, val snapshot: EpisodeSnapshot<E>) {
    val failed: Boolean get() = snapshot.message != null
    val loading: Boolean get() = snapshot.loading && snapshot.items.isEmpty()
    private val empty: Boolean get() = snapshot.items.isEmpty()

    override fun equals(other: Any?) = other is EpisodePhase<*> && seasonKey == other.seasonKey && failed == other.failed && loading == other.loading && empty == other.empty

    override fun hashCode() = listOf(seasonKey, failed, loading, empty).hashCode()
}

/**
 * The chosen season's episodes, as the library's page and Seerr's draw them: grey rows or cards while they load, why they didn't,
 * "no episodes", then the list: rows down the page on a phone, cards in a row on a tablet. Choosing another season swaps the content
 * in place: the area keeps the height of the tallest list it has shown, a later season slides in from the right and an earlier one
 * from the left. [focusKey] is the episode a wide list opens at; the first when it is null or not in the list.
 */
@Composable
internal fun <E, K : Any> EpisodeListArea(
    seasonKey: Any?,
    seasonIndex: Int,
    episodes: EpisodeSnapshot<E>,
    itemKey: (E) -> K,
    wide: Boolean,
    margin: Dp,
    focusKey: K?,
    onRetry: () -> Unit,
    row: @Composable (E) -> Unit,
    card: @Composable (E, Dp) -> Unit,
) {
    val cardWidth = if (wide) EPISODE_CARD_WIDE else EPISODE_CARD
    val currentPhase = EpisodePhase(seasonKey, seasonIndex, episodes)

    // The episode area keeps the height of the tallest list it has shown, so choosing a season swaps its content in place and
    // nothing below it moves.
    val density = LocalDensity.current
    var tallest by remember { mutableStateOf(0.dp) }

    Column(
        verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3),
        modifier = Modifier
            .heightIn(min = tallest)
            .onSizeChanged { size -> with(density) { if (!currentPhase.loading && size.height.toDp() > tallest) tallest = size.height.toDp() } },
    ) {
        AnimatedContent(
            targetState = currentPhase,
            transitionSpec = {
                // A later season slides in from the right, an earlier one from the left; the same season only fades
                val forward = targetState.index > initialState.index
                if (targetState.index == initialState.index) {
                    fadeIn(tween(ROW_FADE_MS)) togetherWith fadeOut(tween(ROW_FADE_MS))
                } else {
                    val sign = if (forward) 1 else -1
                    (slideInHorizontally(tween(200)) { it * sign / 3 } + fadeIn(tween(200))) togetherWith
                        (slideOutHorizontally(tween(200)) { -it * sign / 3 } + fadeOut(tween(140)))
                }
            },
            label = "episodes",
        ) { phase ->
            // The phase on screen follows the live list (a watched mark changes a card, not the phase); one fading out keeps its own
            val list = if (phase == currentPhase) episodes else phase.snapshot

            when {
                phase.failed -> LoadError(list.message ?: "", onRetry)

                phase.loading && wide ->
                    Row(Modifier.bleed(margin).padding(horizontal = margin), horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s4)) {
                        repeat(3) { EpisodeCardSkeleton(cardWidth) }
                    }

                phase.loading ->
                    Column(verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s4)) {
                        repeat(3) { EpisodeRowSkeleton() }
                    }

                list.items.isEmpty() -> Text(Strings.noEpisodes, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

                !wide -> Column(verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s4)) {
                    list.items.distinctBy(itemKey).forEach { episode -> key(itemKey(episode)) { row(episode) } }
                }

                else -> {
                    val listState = rememberLazyListState()
                    val firstKey = itemKey(list.items.first())

                    // A season opens at the episode Play will start, so the one being watched is in view; another season opens at its first
                    LaunchedEffect(firstKey) {
                        val at = list.items.indexOfFirst { itemKey(it) == focusKey }

                        listState.scrollToItem(if (at > 0) at else 0)
                    }

                    LazyRow(
                        state = listState,
                        modifier = Modifier.bleed(margin),
                        contentPadding = PaddingValues(horizontal = margin),
                        horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s4),
                    ) {
                        items(list.items.distinctBy(itemKey), key = { itemKey(it) }) { episode -> card(episode, cardWidth) }
                    }
                }
            }
        }
    }
}
