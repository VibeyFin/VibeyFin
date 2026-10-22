package app.vibeyfin.app.item

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.vibeyfin.app.components.Artwork
import app.vibeyfin.app.components.LoadError
import app.vibeyfin.app.components.ROW_FADE_MS
import app.vibeyfin.app.components.RowPhase
import app.vibeyfin.app.components.SectionTitle
import app.vibeyfin.app.components.SkeletonBlock
import app.vibeyfin.app.components.SkeletonLine
import app.vibeyfin.app.components.VibeyFinMenu
import app.vibeyfin.app.components.VibeyFinMenuItem
import app.vibeyfin.app.components.bleed
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.domain.model.CastMember
import app.vibeyfin.domain.model.MediaItem
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.item.SeasonsState
import app.vibeyfin.ui.item.seasonLabel
import app.vibeyfin.ui.item.TechnicalLine
import coil3.compose.AsyncImage

/**
 * A show's seasons as a row of buttons (the selected one in `accent-soft`, aqua stays for Play), then the selected season's
 * episodes. The row opens scrolled to the season Play starts in; another season swaps the list in place and the old list
 * stays up, a little dimmed, until the new one arrives.
 */
@Composable
fun SeasonsSection(
    seasons: SeasonsState,
    margin: Dp,
    wide: Boolean,
    onSelect: (String) -> Unit,
    onPlayEpisode: (String) -> Unit,
    onToggleEpisodePlayed: (String) -> Unit,
    onRetrySeasons: () -> Unit,
    onRetryEpisodes: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3)) {
        val phase = if (seasons.loading) RowPhase.Loading else if (seasons.message != null) RowPhase.Failed else RowPhase.Ready
        Crossfade(targetState = phase, animationSpec = tween(ROW_FADE_MS), modifier = Modifier.animateContentSize(tween(ROW_FADE_MS))) { shown ->
            when (shown) {
                RowPhase.Loading -> SeasonSkeleton(margin)
                RowPhase.Failed -> LoadError(seasons.message ?: "", onRetrySeasons)
                RowPhase.Ready -> if (seasons.seasons.isEmpty()) {
                    Text(Strings.noSeasons, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    SeasonButtons(seasons, margin, onSelect)
                }
            }
        }
        if (seasons.seasons.isNotEmpty() && !seasons.loading && seasons.message == null) {
            EpisodeList(seasons, wide, margin, onPlayEpisode, onToggleEpisodePlayed, onRetryEpisodes)
        }
    }
}

@Composable
private fun SeasonSkeleton(margin: Dp) {
    Row(Modifier.bleed(margin).padding(horizontal = margin), horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2)) {
        repeat(3) { SkeletonBlock(Modifier.width(96.dp).size(width = 96.dp, height = 32.dp), RoundedCornerShape(VibeyFinTokens.Radius.m3Md)) }
    }
}

/** Each season a button, in the server's order; about four fit and the next peeks. */
@Composable
private fun SeasonButtons(seasons: SeasonsState, margin: Dp, onSelect: (String) -> Unit) {
    val listState = rememberLazyListState()
    // Opens with the season Play starts in in view.
    val opening = seasons.seasons.indexOfFirst { it.id == seasons.selectedId }.coerceAtLeast(0)
    LaunchedEffect(seasons.seasons.size) { if (opening > 0) listState.scrollToItem(opening) }
    LazyRow(
        state = listState,
        modifier = Modifier.bleed(margin).semantics { contentDescription = Strings.seasonsDescription(seasons.seasons.size) },
        contentPadding = PaddingValues(horizontal = margin),
        horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2),
    ) {
        items(seasons.seasons, key = { it.id }) { season ->
            val selected = season.id == seasons.selectedId
            FilterChip(
                selected = selected,
                onClick = { onSelect(season.id) },
                label = { Text(season.seasonLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = FilterChipDefaults.filterChipColors(
                    labelColor = VibeyFinTokens.Colors.inkMuted,
                    selectedContainerColor = VibeyFinTokens.Colors.accentSoft,
                    selectedLabelColor = VibeyFinTokens.Colors.onAccentSoft,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selected,
                    borderColor = VibeyFinTokens.Colors.lineStrong,
                    selectedBorderColor = VibeyFinTokens.Colors.accentSoft,
                ),
                modifier = Modifier.semantics { contentDescription = Strings.seasonDescription(season.seasonLabel, selected) },
            )
        }
    }
}

private data class EpisodePhase(val seasonId: String?, val loading: Boolean, val failed: Boolean)

/** The selected season's episodes; choosing another season fades to its loading cards, then to its episodes. */
@Composable
private fun EpisodeList(seasons: SeasonsState, wide: Boolean, margin: Dp, onPlay: (String) -> Unit, onToggleWatched: (String) -> Unit, onRetry: () -> Unit) {
    val episodes = seasons.episodes
    val cardWidth = if (wide) EPISODE_CARD_WIDE else EPISODE_CARD
    Column(
        verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3),
        modifier = Modifier.animateContentSize(tween(ROW_FADE_MS)),
    ) {
        // The list crossfades between a season loading and a season shown, so a tap never snaps.
        Crossfade(targetState = EpisodePhase(seasons.selectedId, episodes.loading && episodes.items.isEmpty(), episodes.message != null), animationSpec = tween(ROW_FADE_MS), label = "episodes") { phase ->
            when {
                phase.failed -> LoadError(episodes.message ?: "", onRetry)
                phase.loading ->
                    Row(Modifier.bleed(margin).padding(horizontal = margin), horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s4)) {
                        repeat(3) { EpisodeCardSkeleton(cardWidth) }
                    }
                episodes.items.isEmpty() -> Text(Strings.noEpisodes, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> {
                    val listState = rememberLazyListState()
                    // A season opens at the episode Play will start, so the one being watched is in view; another season opens at its first.
                    val firstId = episodes.items.first().id
                    LaunchedEffect(firstId) {
                        val at = episodes.items.indexOfFirst { it.id == seasons.startEpisode?.id }
                        listState.scrollToItem(if (at > 0) at else 0)
                    }
                    LazyRow(
                        state = listState,
                        modifier = Modifier.bleed(margin),
                        contentPadding = PaddingValues(horizontal = margin),
                        horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s4),
                    ) {
                        items(episodes.items.distinctBy { it.id }, key = { it.id }) { episode ->
                            EpisodeCard(episode, cardWidth, current = episode.id == seasons.startEpisode?.id, onPlay = { onPlay(episode.id) }, onToggleWatched = { onToggleWatched(episode.id) })
                        }
                    }
                }
            }
        }
    }
}

/** An episode card is this wide on a phone, and wider on a tablet; its picture is 16:9. */
private val EPISODE_CARD = 260.dp
private val EPISODE_CARD_WIDE = 320.dp

@Composable
private fun EpisodeCardSkeleton(width: Dp) {
    Column(Modifier.width(width), verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2)) {
        SkeletonBlock(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        SkeletonLine(width * 0.7f, MaterialTheme.typography.titleMedium)
        SkeletonLine(96.dp, MaterialTheme.typography.bodyMedium)
        SkeletonLine(width * 0.85f, MaterialTheme.typography.bodyMedium)
    }
}

/**
 * An episode on a tablet: a large card in a row that scrolls sideways. The still on top (with a progress bar when it is part
 * watched), then "4. Title" with a check once watched, how long it is or how much is left, and three lines of synopsis. The
 * one Play starts is the current one. Tapping plays it; a long press offers the watched mark.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeCard(episode: MediaItem, width: Dp, current: Boolean, onPlay: () -> Unit, onToggleWatched: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Column(
            verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2),
            modifier = Modifier
                .width(width)
                .clip(RoundedCornerShape(VibeyFinTokens.Radius.artwork))
                .background(if (current) VibeyFinTokens.Colors.surface2 else androidx.compose.ui.graphics.Color.Transparent)
                .combinedClickable(role = Role.Button, onClick = onPlay, onLongClick = { menu = true })
                .padding(VibeyFinTokens.Space.s2),
        ) {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(VibeyFinTokens.Radius.artwork))) {
                Artwork(
                    url = episode.landscapeUrl ?: episode.posterUrl,
                    title = episode.title,
                    aspectRatio = 16f / 9f,
                    blurHash = episode.landscapeBlurHash,
                    modifier = Modifier.fillMaxWidth(),
                )
                episode.progress?.let { progress ->
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
                        Box(Modifier.fillMaxWidth().size(width = 0.dp, height = 4.dp).background(VibeyFinTokens.Colors.progressTrack))
                        Box(Modifier.fillMaxWidth(progress).size(width = 0.dp, height = 4.dp).background(VibeyFinTokens.Colors.accent))
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2)) {
                Text(
                    text = Strings.episodeTitle(episode.episodeNumber, episode.title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (episode.played) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = Strings.watched, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
            val meta = when {
                episode.progress != null && episode.runtimeMs != null -> Strings.timeLeftShort(episode.runtimeMs!! - episode.resumePositionMs)
                else -> episode.runtimeMinutes?.let(Strings::minutesShort)
            }
            meta?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            // Always three lines, so every card in the row is the same height.
            Text(
                text = episode.overview.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                minLines = 3,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        VibeyFinMenu(expanded = menu, onDismiss = { menu = false }) {
            VibeyFinMenuItem(
                if (episode.played) Strings.markUnwatched else Strings.markWatched,
                if (episode.played) Icons.Default.Clear else Icons.Default.Check,
            ) { menu = false; onToggleWatched() }
        }
    }
}

/** A row of the actors: a round picture, the name and the part they play. A person with no picture is a grey disc with initials. */
@Composable
fun CastSection(cast: List<CastMember>, margin: Dp, tile: Dp) {
    Column(verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3)) {
        SectionTitle(Strings.cast, modifier = Modifier.semantics { heading() })
        LazyRow(
            modifier = Modifier.bleed(margin),
            contentPadding = PaddingValues(horizontal = margin),
            horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3),
        ) {
            items(cast.distinctBy { it.id }, key = { it.id }) { person -> CastTile(person, tile) }
        }
    }
}

@Composable
private fun CastTile(person: CastMember, tile: Dp) {
    Column(
        modifier = Modifier.width(tile),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s1),
    ) {
        Box(Modifier.size(tile).clip(CircleShape).background(VibeyFinTokens.Colors.surface2), contentAlignment = Alignment.Center) {
            Text(initials(person.name), style = MaterialTheme.typography.titleMedium, color = VibeyFinTokens.Colors.inkMuted)
            if (person.imageUrl != null) {
                AsyncImage(model = person.imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchSize())
            }
        }
        // Names take two lines and roles one, always, so the row keeps one height.
        Text(person.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(person.role.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, minLines = 1, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun Modifier.matchSize(): Modifier = this.fillMaxWidth().fillMaxHeight()

private fun initials(name: String): String =
    name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }

/** The file's details, collapsed until asked for. */
@Composable
fun TechnicalInfo(lines: List<TechnicalLine>) {
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { open = !open }.padding(vertical = VibeyFinTokens.Space.s2),
        ) {
            SectionTitle(Strings.technicalInfo, modifier = Modifier.weight(1f))
            Icon(if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (open) {
            lines.forEach { line ->
                Column {
                    Text(line.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(line.value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
                }
            }
        }
    }
}
