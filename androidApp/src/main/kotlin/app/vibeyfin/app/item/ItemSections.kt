package app.vibeyfin.app.item

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.material.icons.filled.Info
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
import app.vibeyfin.ui.item.EpisodeList as EpisodeData
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
    onViewEpisode: (MediaItem) -> Unit,
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
            EpisodeList(seasons, wide, margin, onPlayEpisode, onToggleEpisodePlayed, onViewEpisode, onRetryEpisodes)
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
                // No outline: a filled button, softly lit when selected.
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = VibeyFinTokens.Colors.surface2,
                    labelColor = VibeyFinTokens.Colors.inkMuted,
                    selectedContainerColor = VibeyFinTokens.Colors.accentSoft,
                    selectedLabelColor = VibeyFinTokens.Colors.onAccentSoft,
                ),
                border = null,
                modifier = Modifier.semantics { contentDescription = Strings.seasonDescription(season.seasonLabel, selected) },
            )
        }
    }
}

/**
 * What the episode area shows: loading cards, a failure, or the list. Two phases are equal when they show the same kind of
 * thing for the same season, so the area only animates on a real change; [snapshot] is what the phase showed when it was made,
 * kept so that a phase fading out keeps drawing its own content, not the next one's.
 */
private class EpisodePhase(val seasonId: String?, val index: Int, val snapshot: EpisodeData) {
    val failed: Boolean get() = snapshot.message != null
    val loading: Boolean get() = snapshot.loading && snapshot.items.isEmpty()
    private val empty: Boolean get() = snapshot.items.isEmpty()
    override fun equals(other: Any?) = other is EpisodePhase && seasonId == other.seasonId && failed == other.failed && loading == other.loading && empty == other.empty
    override fun hashCode() = listOf(seasonId, failed, loading, empty).hashCode()
}

/** The selected season's episodes; choosing another season fades to its loading cards, then to its episodes. */
@Composable
private fun EpisodeList(seasons: SeasonsState, wide: Boolean, margin: Dp, onPlay: (String) -> Unit, onToggleWatched: (String) -> Unit, onViewEpisode: (MediaItem) -> Unit, onRetry: () -> Unit) {
    val episodes = seasons.episodes
    val cardWidth = if (wide) EPISODE_CARD_WIDE else EPISODE_CARD
    val currentPhase = EpisodePhase(seasons.selectedId, seasons.seasons.indexOfFirst { it.id == seasons.selectedId }, episodes)
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
        // The list crossfades between a season loading and a season shown, so a tap never snaps.
        AnimatedContent(
            targetState = currentPhase,
            transitionSpec = {
                // A later season slides in from the right, an earlier one from the left; the same season only fades.
                val forward = targetState.index > initialState.index
                if (targetState.index == initialState.index) {
                    fadeIn(tween(ROW_FADE_MS)) togetherWith fadeOut(tween(ROW_FADE_MS))
                } else {
                    val sign = if (forward) 1 else -1
                    (slideInHorizontally(tween(300)) { it * sign / 3 } + fadeIn(tween(300))) togetherWith
                        (slideOutHorizontally(tween(300)) { -it * sign / 3 } + fadeOut(tween(200)))
                }
            },
            label = "episodes",
        ) { phase ->
            // The phase on screen follows the live list (a watched mark changes a card, not the phase); one fading out keeps its own.
            val list = if (phase == currentPhase) episodes else phase.snapshot
            when {
                phase.failed -> LoadError(list.message ?: "", onRetry)
                phase.loading ->
                    Row(Modifier.bleed(margin).padding(horizontal = margin), horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s4)) {
                        repeat(3) { EpisodeCardSkeleton(cardWidth) }
                    }
                list.items.isEmpty() -> Text(Strings.noEpisodes, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> {
                    val listState = rememberLazyListState()
                    // A season opens at the episode Play will start, so the one being watched is in view; another season opens at its first.
                    val firstId = list.items.first().id
                    LaunchedEffect(firstId) {
                        val at = list.items.indexOfFirst { it.id == seasons.startEpisode?.id }
                        listState.scrollToItem(if (at > 0) at else 0)
                    }
                    LazyRow(
                        state = listState,
                        modifier = Modifier.bleed(margin),
                        contentPadding = PaddingValues(horizontal = margin),
                        horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s4),
                    ) {
                        items(list.items.distinctBy { it.id }, key = { it.id }) { episode ->
                            EpisodeCard(episode, cardWidth, current = episode.id == seasons.startEpisode?.id, onPlay = { onPlay(episode.id) }, onToggleWatched = { onToggleWatched(episode.id) }, onView = { onViewEpisode(episode) })
                        }
                    }
                }
            }
        }
    }
}

/** An episode card is this wide on a phone, and wider on a tablet. */
private val EPISODE_CARD = 300.dp
private val EPISODE_CARD_WIDE = 380.dp

/** An episode card is this much wider than tall. */
private const val EPISODE_ASPECT = 1.6f

@Composable
private fun EpisodeCardSkeleton(width: Dp) {
    SkeletonBlock(Modifier.width(width).aspectRatio(EPISODE_ASPECT))
}

/**
 * An episode: a card in a row that scrolls sideways, all of it on the still. At the bottom, over a scrim: the "S1 E4" tag, the
 * title, two lines of synopsis, and a line with how long it is (or how much is left), its rating and the day it aired. A check
 * sits at the top once watched, a progress bar runs along the bottom edge when it is part watched, and the one Play starts has a
 * brighter outline. Tapping plays it; a long press opens a menu: View episode, and the watched mark.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeCard(episode: MediaItem, width: Dp, current: Boolean, onPlay: () -> Unit, onToggleWatched: () -> Unit, onView: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(VibeyFinTokens.Radius.artwork)
    Box {
        Box(
            Modifier
                .width(width)
                .aspectRatio(EPISODE_ASPECT)
                .clip(shape)
                .border(1.dp, if (current) VibeyFinTokens.Colors.lineStrong else VibeyFinTokens.Colors.surface3, shape)
                .combinedClickable(role = Role.Button, onClick = onPlay, onLongClick = { menu = true }),
        ) {
            Artwork(
                url = episode.landscapeUrl ?: episode.posterUrl,
                title = episode.title,
                aspectRatio = EPISODE_ASPECT,
                blurHash = episode.landscapeBlurHash,
                modifier = Modifier.fillMaxSize(),
            )
            // The scrim the text sits on: the picture stays clear well down the card, and only the lower part darkens (to about
            // three quarters), just enough for the white text to read.
            Box(
                Modifier.matchParentSize().background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        0f to androidx.compose.ui.graphics.Color.Transparent,
                        0.4f to androidx.compose.ui.graphics.Color.Transparent,
                        0.75f to androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f),
                        1f to androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.78f),
                    ),
                ),
            )
            if (episode.played) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = Strings.watched,
                    tint = VibeyFinTokens.Colors.ink,
                    modifier = Modifier.align(Alignment.TopEnd).padding(VibeyFinTokens.Space.s3).size(22.dp),
                )
            }
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = VibeyFinTokens.Space.s3)
                    .padding(bottom = VibeyFinTokens.Space.s3 + if (episode.progress != null) 4.dp else 0.dp),
                verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s1),
            ) {
                Strings.episodeTag(episode.seasonNumber, episode.episodeNumber)?.let { EpisodeTag(it) }
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                    color = VibeyFinTokens.Colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Two lines, always, so every card in the row is the same.
                Text(
                    text = episode.overview.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = VibeyFinTokens.Colors.ink,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val time = when {
                    episode.progress != null && episode.runtimeMs != null -> Strings.timeLeftShort(episode.runtimeMs!! - episode.resumePositionMs)
                    else -> episode.runtimeMinutes?.let(Strings::minutesShort)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3)) {
                    time?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = VibeyFinTokens.Colors.ink) }
                    episode.rating?.takeIf { it > 0 }?.let { Text(Strings.ratingTag(it), style = MaterialTheme.typography.labelLarge, color = VibeyFinTokens.Colors.ink) }
                    Spacer(Modifier.weight(1f))
                    Strings.releaseDate(episode.premiereDate)?.let {
                        Text(it, style = MaterialTheme.typography.labelMedium, color = VibeyFinTokens.Colors.inkMuted, maxLines = 1)
                    }
                }
            }
            episode.progress?.let { progress ->
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().size(width = 0.dp, height = 4.dp).background(VibeyFinTokens.Colors.progressTrack))
                    Box(Modifier.fillMaxWidth(progress).size(width = 0.dp, height = 4.dp).background(VibeyFinTokens.Colors.accent))
                }
            }
        }
        VibeyFinMenu(expanded = menu, onDismiss = { menu = false }) {
            VibeyFinMenuItem(Strings.viewEpisode, Icons.Default.Info) { menu = false; onView() }
            VibeyFinMenuItem(
                if (episode.played) Strings.markUnwatched else Strings.markWatched,
                if (episode.played) Icons.Default.Clear else Icons.Default.Check,
            ) { menu = false; onToggleWatched() }
        }
    }
}

/** A small tag over a picture: how long, how well rated, which age. */
@Composable
private fun EpisodeTag(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = VibeyFinTokens.Colors.ink,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(VibeyFinTokens.Colors.scrim)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** A row of the actors: a round picture, the name and the part they play; one opens their page. A person with no picture is a grey disc with initials. */
@Composable
fun CastSection(cast: List<CastMember>, margin: Dp, tile: Dp, onOpenPerson: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3)) {
        SectionTitle(Strings.cast, modifier = Modifier.semantics { heading() })
        LazyRow(
            modifier = Modifier.bleed(margin),
            contentPadding = PaddingValues(horizontal = margin),
            horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3),
        ) {
            items(cast.distinctBy { it.id }, key = { it.id }) { person -> CastTile(person, tile, onClick = { onOpenPerson(person.id) }) }
        }
    }
}

@Composable
private fun CastTile(person: CastMember, tile: Dp, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(tile)
            .clip(RoundedCornerShape(VibeyFinTokens.Radius.m3Md))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = Strings.personDescription(person.name, person.role) },
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
