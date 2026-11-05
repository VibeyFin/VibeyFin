package app.vibeyfin.app.item

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.vibeyfin.R
import app.vibeyfin.app.components.Artwork
import app.vibeyfin.app.components.ArtworkTile
import app.vibeyfin.app.components.LoadError
import app.vibeyfin.app.components.MediaRowSection
import app.vibeyfin.app.components.POSTER_CAPTION_LINES
import app.vibeyfin.app.components.PlaybackProgressBar
import app.vibeyfin.app.components.ROW_FADE_MS
import app.vibeyfin.app.components.RowPhase
import app.vibeyfin.app.components.SectionTitle
import app.vibeyfin.app.components.SkeletonBlock
import app.vibeyfin.app.components.VibeyFinMenu
import app.vibeyfin.app.components.VibeyFinMenuItem
import app.vibeyfin.app.components.bleed
import app.vibeyfin.app.downloads.DownloadActionMenuItem
import app.vibeyfin.app.downloads.EpisodeDownloadButton
import app.vibeyfin.app.downloads.rememberDownloadNotificationPermission
import app.vibeyfin.app.seerr.SeerrTile
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.domain.Download
import app.vibeyfin.domain.model.CastMember
import app.vibeyfin.domain.model.MediaItem
import app.vibeyfin.domain.model.SeerrTitle
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.downloads.actions
import app.vibeyfin.ui.home.MediaRow
import app.vibeyfin.ui.item.SeasonsState
import app.vibeyfin.ui.item.SimilarTile
import app.vibeyfin.ui.item.TechnicalLine
import app.vibeyfin.ui.item.seasonLabel
import coil3.compose.AsyncImage
import app.vibeyfin.ui.item.EpisodeList as EpisodeData

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
    downloads: Map<String, Download>,
    canDownload: Boolean,
    downloadCallbacks: DownloadCallbacks,
) {
    Column(verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3)) {
        val phase = if (seasons.loading) {
            RowPhase.Loading
        } else if (seasons.message != null) {
            RowPhase.Failed
        } else {
            RowPhase.Ready
        }

        Crossfade(targetState = phase, animationSpec = tween(ROW_FADE_MS), modifier = Modifier.animateContentSize(tween(ROW_FADE_MS))) { shown ->
            when (shown) {
                RowPhase.Loading -> SeasonSkeleton(margin)

                RowPhase.Failed -> LoadError(seasons.message ?: "", onRetrySeasons)

                RowPhase.Ready -> if (seasons.seasons.isEmpty()) {
                    Text(Strings.noSeasons, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    SeasonChipsRow(seasons.seasons.map { SeasonChip(it.id, it.seasonLabel) }, seasons.selectedId, margin, onSelect)
                }
            }
        }

        if (seasons.seasons.isNotEmpty() && !seasons.loading && seasons.message == null) {
            EpisodeList(seasons, wide, margin, onPlayEpisode, onToggleEpisodePlayed, onViewEpisode, onRetryEpisodes, downloads, canDownload, downloadCallbacks)
        }
    }
}

@Composable
private fun SeasonSkeleton(margin: Dp) {
    Row(Modifier.bleed(margin).padding(horizontal = margin), horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2)) {
        repeat(3) { SkeletonBlock(Modifier.width(96.dp).size(width = 96.dp, height = 32.dp), RoundedCornerShape(VibeyFinTokens.Radius.m3Md)) }
    }
}

@Composable
private fun EpisodeList(
    seasons: SeasonsState,
    wide: Boolean,
    margin: Dp,
    onPlay: (String) -> Unit,
    onToggleWatched: (String) -> Unit,
    onViewEpisode: (MediaItem) -> Unit,
    onRetry: () -> Unit,
    downloads: Map<String, Download>,
    canDownload: Boolean,
    downloadCallbacks: DownloadCallbacks,
) {
    val episodes = seasons.episodes

    EpisodeListArea(
        seasonKey = seasons.selectedId,
        seasonIndex = seasons.seasons.indexOfFirst { it.id == seasons.selectedId },
        episodes = EpisodeSnapshot(episodes.items, episodes.loading, episodes.message),
        itemKey = { it.id },
        wide = wide,
        margin = margin,
        focusKey = seasons.startEpisode?.id,
        onRetry = onRetry,
        row = { episode ->
            EpisodeRow(
                episode,
                current = episode.id == seasons.startEpisode?.id,
                onPlay = { onPlay(episode.id) },
                onToggleWatched = { onToggleWatched(episode.id) },
                onView = { onViewEpisode(episode) },
                download = downloads[episode.id],
                canDownload = canDownload,
                downloadCallbacks = downloadCallbacks,
            )
        },
        card = { episode, width ->
            EpisodeCard(
                episode, width, current = episode.id == seasons.startEpisode?.id, onPlay = { onPlay(episode.id) }, onToggleWatched = { onToggleWatched(episode.id) }, onView = { onViewEpisode(episode) },
                download = downloads[episode.id], canDownload = canDownload, downloadCallbacks = downloadCallbacks,
            )
        },
    )
}

internal val EPISODE_CARD = 300.dp
internal val EPISODE_CARD_WIDE = 380.dp

internal const val EPISODE_ASPECT = 1.6f

@Composable
internal fun EpisodeCardSkeleton(width: Dp) {
    SkeletonBlock(Modifier.width(width).aspectRatio(EPISODE_ASPECT))
}

internal val EPISODE_ROW_PICTURE = 148.dp

@Composable
internal fun EpisodeRowSkeleton() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3)) {
        SkeletonBlock(Modifier.width(EPISODE_ROW_PICTURE).aspectRatio(EPISODE_ASPECT))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2)) {
            SkeletonBlock(Modifier.fillMaxWidth(0.7f).height(16.dp))
            SkeletonBlock(Modifier.fillMaxWidth(0.4f).height(12.dp))
            SkeletonBlock(Modifier.fillMaxWidth().height(12.dp))
            SkeletonBlock(Modifier.fillMaxWidth(0.8f).height(12.dp))
        }
    }
}

private fun episodeTime(episode: MediaItem): String? = when {
    episode.progress != null && episode.runtimeMs != null -> Strings.timeLeftShort(episode.runtimeMs!! - episode.resumePositionMs)
    else -> episode.runtimeMinutes?.let(Strings::minutesShort)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeRow(
    episode: MediaItem,
    current: Boolean,
    onPlay: () -> Unit,
    onToggleWatched: () -> Unit,
    onView: () -> Unit,
    download: Download?,
    canDownload: Boolean,
    downloadCallbacks: DownloadCallbacks,
) {
    var menu by remember { mutableStateOf(false) }
    val ensureNotifications = rememberDownloadNotificationPermission()
    val shape = RoundedCornerShape(VibeyFinTokens.Radius.artwork)

    Box {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3)) {
            Box(
                Modifier
                    .width(EPISODE_ROW_PICTURE)
                    .aspectRatio(EPISODE_ASPECT)
                    .clip(shape)
                    .border(1.dp, if (current) VibeyFinTokens.Colors.lineStrong else VibeyFinTokens.Colors.surface3, shape)
                    .combinedClickable(role = Role.Button, onLongClickLabel = Strings.showOptions, onClick = onPlay, onLongClick = { menu = true }),
            ) {
                Artwork(
                    url = episode.landscapeUrl ?: episode.posterUrl,
                    title = episode.title,
                    aspectRatio = EPISODE_ASPECT,
                    blurHash = episode.landscapeBlurHash,
                    modifier = Modifier.fillMaxSize(),
                )

                if (episode.played) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = Strings.watched,
                        tint = VibeyFinTokens.Colors.ink,
                        modifier = Modifier.align(Alignment.TopEnd).padding(VibeyFinTokens.Space.s2).size(20.dp),
                    )
                }

                episode.progress?.let { progress ->
                    PlaybackProgressBar(progress, Modifier.align(Alignment.BottomStart))
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .combinedClickable(role = Role.Button, onLongClickLabel = Strings.showOptions, onClick = onView, onLongClick = { menu = true }),
                verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s1),
            ) {
                Strings.episodeTag(episode.seasonNumber, episode.episodeNumber)?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = VibeyFinTokens.Colors.inkMuted, maxLines = 1)
                }

                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = VibeyFinTokens.Colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                val facts = listOfNotNull(
                    episodeTime(episode),
                    episode.rating?.takeIf { it > 0 }?.let(Strings::ratingTag),
                    Strings.releaseDate(episode.premiereDate),
                )

                if (facts.isNotEmpty()) {
                    Text(Strings.factsLine(facts), style = MaterialTheme.typography.labelMedium, color = VibeyFinTokens.Colors.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                // Two lines, always, so every row is the same height
                Text(
                    text = episode.overview.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = VibeyFinTokens.Colors.ink,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            EpisodeDownloadButton(
                title = episode.title,
                download = download,
                canDownload = canDownload,
                onStart = { downloadCallbacks.onDownloadEpisode(episode.id) },
                onAction = { downloadCallbacks.onAction(episode.id, it) },
            )
        }

        VibeyFinMenu(expanded = menu, onDismiss = { menu = false }) {
            VibeyFinMenuItem(Strings.viewEpisode, Icons.Default.Info) {
                menu = false

                onView()
            }

            VibeyFinMenuItem(
                if (episode.played) Strings.markUnwatched else Strings.markWatched,
                if (episode.played) Icons.Default.Clear else Icons.Default.Check,
            ) {
                menu = false

                onToggleWatched()
            }

            EpisodeDownloadItems(episode.id, download, canDownload, downloadCallbacks, ensureNotifications) { menu = false }
        }
    }
}

@Composable
private fun EpisodeDownloadItems(
    episodeId: String,
    download: Download?,
    canDownload: Boolean,
    callbacks: DownloadCallbacks,
    ensureNotifications: () -> Unit,
    close: () -> Unit,
) {
    if (download != null) {
        download.actions.forEach { action ->
            DownloadActionMenuItem(action) {
                close()
                callbacks.onAction(episodeId, action)
            }
        }
    } else if (canDownload) {
        VibeyFinMenuItem(Strings.downloadEpisode, ImageVector.vectorResource(R.drawable.ic_download)) {
            close()
            ensureNotifications()
            callbacks.onDownloadEpisode(episodeId)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeCard(
    episode: MediaItem,
    width: Dp,
    current: Boolean,
    onPlay: () -> Unit,
    onToggleWatched: () -> Unit,
    onView: () -> Unit,
    download: Download?,
    canDownload: Boolean,
    downloadCallbacks: DownloadCallbacks,
) {
    var menu by remember { mutableStateOf(false) }
    val ensureNotifications = rememberDownloadNotificationPermission()
    val shape = RoundedCornerShape(VibeyFinTokens.Radius.artwork)

    Box {
        Box(
            Modifier
                .width(width)
                .aspectRatio(EPISODE_ASPECT)
                .clip(shape)
                .border(1.dp, if (current) VibeyFinTokens.Colors.lineStrong else VibeyFinTokens.Colors.surface3, shape)
                .combinedClickable(role = Role.Button, onLongClickLabel = Strings.showOptions, onClick = onPlay, onLongClick = { menu = true }),
        ) {
            Artwork(
                url = episode.landscapeUrl ?: episode.posterUrl,
                title = episode.title,
                aspectRatio = EPISODE_ASPECT,
                blurHash = episode.landscapeBlurHash,
                modifier = Modifier.fillMaxSize(),
            )

            EpisodeCardScrim()

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
                    .combinedClickable(role = Role.Button, onLongClickLabel = Strings.showOptions, onClick = onView, onLongClick = { menu = true })
                    .padding(horizontal = VibeyFinTokens.Space.s3)
                    .padding(bottom = VibeyFinTokens.Space.s3 + if (episode.progress != null) 4.dp else 0.dp),
                verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s1),
            ) {
                Strings.episodeTag(episode.seasonNumber, episode.episodeNumber)?.let { EpisodeTag(it) }
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = VibeyFinTokens.Colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // Two lines, always, so every card in the row is the same
                Text(
                    text = episode.overview.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = VibeyFinTokens.Colors.ink,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                EpisodeFacts(episode, Modifier.padding(end = if (download != null || canDownload) DOWNLOAD_BUTTON_ROOM else 0.dp))
            }

            episode.progress?.let { progress ->
                PlaybackProgressBar(progress, Modifier.align(Alignment.BottomStart))
            }

            EpisodeDownloadButton(
                title = episode.title,
                download = download,
                canDownload = canDownload,
                onStart = { downloadCallbacks.onDownloadEpisode(episode.id) },
                onAction = { downloadCallbacks.onAction(episode.id, it) },
                overPicture = true,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }

        VibeyFinMenu(expanded = menu, onDismiss = { menu = false }) {
            VibeyFinMenuItem(Strings.viewEpisode, Icons.Default.Info) {
                menu = false

                onView()
            }

            VibeyFinMenuItem(
                if (episode.played) Strings.markUnwatched else Strings.markWatched,
                if (episode.played) Icons.Default.Clear else Icons.Default.Check,
            ) {
                menu = false

                onToggleWatched()
            }

            EpisodeDownloadItems(episode.id, download, canDownload, downloadCallbacks, ensureNotifications) { menu = false }
        }
    }
}

@Composable
internal fun EpisodeTag(text: String) {
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

/** A row of the actors: a round picture, the name and the part they play; one opens their page. A person with no picture is a gray disc with initials. */
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
            Text(Strings.initials(person.name), style = MaterialTheme.typography.titleMedium, color = VibeyFinTokens.Colors.inkMuted)

            if (person.imageUrl != null) {
                AsyncImage(model = person.imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchSize())
            }
        }

        // Names take two lines and roles one, always, so the row keeps one height
        Text(person.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(person.role.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, minLines = 1, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun Modifier.matchSize(): Modifier = this.fillMaxWidth().fillMaxHeight()

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

// The scrim the text sits on: the picture stays clear well down the card, and only the lower part darkens, just enough for white text to read
@Composable
internal fun BoxScope.EpisodeCardScrim() {
    Box(
        Modifier.matchParentSize().background(
            Brush.verticalGradient(
                0f to Color.Transparent,
                0.4f to Color.Transparent,
                0.75f to Color.Black.copy(alpha = 0.55f),
                1f to Color.Black.copy(alpha = 0.78f),
            ),
        ),
    )
}

// The facts line stops this far short of the card's download button at the bottom-right
private val DOWNLOAD_BUTTON_ROOM = 36.dp

@Composable
private fun EpisodeFacts(episode: MediaItem, modifier: Modifier = Modifier) {
    val time = episodeTime(episode)

    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3)) {
        time?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = VibeyFinTokens.Colors.ink) }
        episode.rating?.takeIf { it > 0 }?.let { Text(Strings.ratingTag(it), style = MaterialTheme.typography.labelLarge, color = VibeyFinTokens.Colors.ink) }
        Spacer(Modifier.weight(1f))
        Strings.releaseDate(episode.premiereDate)?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, color = VibeyFinTokens.Colors.inkMuted, maxLines = 1)
        }
    }
}

/**
 * "More like this": the library's titles with Seerr's mixed in (they can be requested; a press and hold offers the Seerr menu).
 * While it loads or when it fails it is the library's row; once read, the tiles the view model mixed.
 */
@Composable
fun SimilarSection(row: MediaRow, tiles: List<SimilarTile>, margin: Dp, onOpen: (MediaItem) -> Unit, onOpenSeerr: (SeerrTitle) -> Unit, onRetry: () -> Unit, tileWidth: Dp) {
    if (row.loading || row.message != null || tiles.none { it is SimilarTile.Seerr }) {
        MediaRowSection(row, margin, onOpen, onSeeAll = null, onRetry = onRetry, tileWidth = tileWidth)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3)) {
        SectionTitle(row.title, maxLines = 1, modifier = Modifier.semantics { heading() })
        LazyRow(
            modifier = Modifier.bleed(margin),
            contentPadding = PaddingValues(horizontal = margin),
            horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3),
        ) {
            items(tiles, key = { it.key }, contentType = { if (it is SimilarTile.Seerr) "seerr-poster" else "poster" }) { tile ->
                when (tile) {
                    is SimilarTile.Library -> ArtworkTile(tile.item, onClick = { onOpen(tile.item) }, modifier = Modifier.width(tileWidth), captionLines = POSTER_CAPTION_LINES)
                    is SimilarTile.Seerr -> SeerrTile(tile.title, onOpenSeerr, Modifier.width(tileWidth))
                }
            }
        }
    }
}
