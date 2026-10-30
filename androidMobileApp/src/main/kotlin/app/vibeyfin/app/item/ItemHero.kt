package app.vibeyfin.app.item

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vibeyfin.R
import app.vibeyfin.app.components.Artwork
import app.vibeyfin.app.components.SkeletonBlock
import app.vibeyfin.app.components.VibeyFinMenu
import app.vibeyfin.app.components.VibeyFinMenuItem
import app.vibeyfin.app.downloads.DownloadActionMenuItem
import app.vibeyfin.app.downloads.DownloadBadgeIcon
import app.vibeyfin.app.downloads.rememberDownloadNotificationPermission
import app.vibeyfin.app.navigation.LocalOpenTrailerUrl
import app.vibeyfin.app.theme.BrandDisplay
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.domain.model.ItemKind
import app.vibeyfin.domain.model.MediaItem
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.downloads.actions
import app.vibeyfin.ui.item.ItemUiState
import app.vibeyfin.ui.item.Trailer
import app.vibeyfin.ui.item.seasonLabel
import coil3.compose.AsyncImage

/**
 * A tablet's (or an unfolded foldable's) backdrop: the picture fills the whole screen and stays put while the page scrolls over it.
 * It darkens as the page scrolls ([progress] from 0 at the top to 1 once the first screen is gone), so what scrolls up reads on
 * it. A scrim from the left sits behind the first screen's column of text, and a band at the top behind the back button.
 */
@Composable
fun ItemBackdrop(item: MediaItem, screenWidth: Dp, screenHeight: Dp, progress: Float, modifier: Modifier = Modifier) {
    Box(modifier) {
        val picture = item.backdropUrl ?: item.posterUrl

        if (picture != null) {
            Artwork(
                url = picture,
                title = item.title,
                aspectRatio = screenWidth.value / screenHeight.value,
                blurHash = if (item.backdropUrl != null) item.backdropBlurHash else item.posterBlurHash,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize().background(VibeyFinTokens.Colors.surface2))
        }

        val scrim = VibeyFinTokens.Colors.scrim

        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(0f to VibeyFinTokens.Colors.scrimStrong, 0.3f to scrim, 0.65f to Color.Transparent)))
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to scrim, 0.16f to Color.Transparent, 0.6f to Color.Transparent, 1f to scrim)))

        // The darkening: from the picture as it is to nearly the page's own color, as the page scrolls up over it
        Box(Modifier.matchParentSize().background(VibeyFinTokens.Colors.bg.copy(alpha = MAX_DARKEN * progress.coerceIn(0f, 1f))))
    }
}

/**
 * A phone's picture: pinned at the top, [height] tall, and staying put while the page scrolls: the text goes up over it, and it
 * darkens ([scroll] against [height]) so what scrolls over it reads. A scrim under the text fades it into the page.
 */
@Composable
fun PinnedBackdrop(item: MediaItem, height: Dp, scroll: ScrollState, modifier: Modifier = Modifier) {
    // Fully dark after about half the picture has scrolled by, so the page reads well before the picture is gone
    val darkenOverPx = with(LocalDensity.current) { height.toPx() } * DARKEN_OVER

    Box(modifier.fillMaxWidth().height(height)) {
        val picture = item.backdropUrl ?: item.posterUrl

        if (picture != null) {
            Artwork(
                url = picture,
                title = item.title,
                aspectRatio = 2f / 3f,
                blurHash = if (item.backdropUrl != null) item.backdropBlurHash else item.posterBlurHash,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize().background(VibeyFinTokens.Colors.surface2))
        }

        val scrim = VibeyFinTokens.Colors.scrim
        val scrimStrong = VibeyFinTokens.Colors.scrimStrong

        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(0f to scrim, 0.18f to Color.Transparent, 0.40f to Color.Transparent, 0.60f to scrim, 0.82f to scrimStrong, 1f to VibeyFinTokens.Colors.bg),
            ),
        )

        // The darkening as the page scrolls; read in the draw step, so scrolling redraws this layer and nothing else
        Box(
            Modifier.matchParentSize().graphicsLayer { alpha = (scroll.value / darkenOverPx).coerceIn(0f, 1f) * MAX_DARKEN }.background(VibeyFinTokens.Colors.bg),
        )
    }
}

private const val DARKEN_OVER = 0.55f

// Nearly the page's own color, with a hint of the picture left
private const val MAX_DARKEN = 0.94f

/** The hero's content: the show it belongs to, the title, the facts, the overview, Play and the actions. Centred on a phone, at the start on a tablet. */
@Composable
fun HeroDetails(
    item: MediaItem,
    state: ItemUiState,
    centered: Boolean,
    onPlay: (startOver: Boolean) -> Unit,
    onOpenSeries: (String) -> Unit,
    onToggleFavorite: () -> Unit,
    onPlayTrailer: (String) -> Unit,
    onTogglePlayed: () -> Unit,
    downloads: DownloadCallbacks,
) {
    Column(
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item.seriesName?.let { name ->
            val seriesId = item.seriesId

            if (seriesId != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(role = Role.Button) { onOpenSeries(seriesId) }
                        .semantics { contentDescription = Strings.goToSeries(name) }
                        .padding(horizontal = VibeyFinTokens.Space.s2),
                ) {
                    Text(name, style = MaterialTheme.typography.labelLarge, color = VibeyFinTokens.Colors.inkMuted)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = VibeyFinTokens.Colors.inkMuted, modifier = Modifier.size(18.dp))
                }
            } else {
                Text(name, style = MaterialTheme.typography.labelLarge, color = VibeyFinTokens.Colors.inkMuted)
            }
        }

        TitleArt(item, centered = centered)
        FactsLine(state, centered = centered)

        if (item.genres.isNotEmpty()) {
            Text(
                text = Strings.factsLine(item.genres),
                style = MaterialTheme.typography.bodyMedium,
                color = VibeyFinTokens.Colors.inkMuted,
                textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            )
        }

        item.overview?.let { Overview(it) }

        // A show's Play waits for its next episode: a gray button of the same size holds its place, so it does not pop in
        if (state.canPlay) {
            PlayButtons(state.playLabel, state.canStartOver, onPlay, fullWidth = true)
        } else if (state.playLoading) {
            SkeletonBlock(Modifier.fillMaxWidth().height(VibeyFinTokens.Size.buttonLarge), shape = RoundedCornerShape(50))
        }

        ActionButtons(state, onToggleFavorite, onPlayTrailer, onTogglePlayed, downloads)
    }
}

// Four places of one width, so fewer actions (an episode has no trailer) keep their place and the last ones stay empty
@Composable
private fun ActionButtons(state: ItemUiState, onToggleFavorite: () -> Unit, onPlayTrailer: (String) -> Unit, onTogglePlayed: () -> Unit, downloads: DownloadCallbacks) {
    val openTrailerUrl = LocalOpenTrailerUrl.current

    ActionRow(Modifier.fillMaxWidth()) {
        ActionButton(
            label = Strings.myList,
            description = Strings.myListAction(state.isFavorite),
            icon = { modifier -> Icon(rememberVectorPainter(if (state.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder), contentDescription = null, tint = VibeyFinTokens.Colors.ink, modifier = modifier) },
            popKey = state.isFavorite,
            onClick = onToggleFavorite,
        )

        val trailer = state.trailer

        if (trailer != null) {
            ActionButton(label = Strings.trailer, description = Strings.trailer, icon = { modifier -> Icon(painterResource(R.drawable.ic_trailer), contentDescription = null, tint = VibeyFinTokens.Colors.ink, modifier = modifier) }) {
                when (trailer) {
                    is Trailer.Local -> onPlayTrailer(trailer.itemId)
                    is Trailer.Remote -> openTrailerUrl(trailer.url)
                }
            }
        }

        if (state.canMark) {
            ActionButton(
                label = Strings.watchedAction,
                description = state.markLabel,
                icon = { modifier -> Icon(rememberVectorPainter(if (state.watched) Icons.Default.CheckCircle else Icons.Outlined.CheckCircle), contentDescription = null, tint = VibeyFinTokens.Colors.ink, modifier = modifier) },
                popKey = state.watched,
                onClick = onTogglePlayed,
            )
        }

        if (state.offersDownload) DownloadButton(state, downloads)
    }
}

// Tapping starts a movie or an episode with no download; otherwise, and for a show, it opens a menu
@Composable
private fun DownloadButton(state: ItemUiState, downloads: DownloadCallbacks) {
    var menu by remember { mutableStateOf(false) }
    val ensureNotifications = rememberDownloadNotificationPermission()
    val item = state.item ?: return
    val isSeries = item.kind == ItemKind.Series

    ActionButton(
        label = state.downloadLabel,
        description = state.downloadDescription,
        icon = { modifier -> DownloadBadgeIcon(state.downloadBadge, state.downloadProgress, modifier) },
        onClick = {
            if (!isSeries && state.download == null) {
                ensureNotifications()
                downloads.onDownload()
            } else {
                menu = true
            }
        },
        menu = {
            VibeyFinMenu(expanded = menu, onDismiss = { menu = false }) {
                if (isSeries) {
                    val season = state.seasons.seasons.firstOrNull { it.id == state.seasons.selectedId }
                    if (season != null) {
                        VibeyFinMenuItem(Strings.downloadSeasonNamed(season.seasonLabel), painterVector(R.drawable.ic_download)) {
                            menu = false
                            downloads.onDownloadSeason(season.id)
                        }
                    }
                    VibeyFinMenuItem(Strings.downloadAllEpisodes, painterVector(R.drawable.ic_download)) {
                        menu = false
                        downloads.onDownloadSeries()
                    }
                    if (state.seriesDownloads.isNotEmpty()) {
                        VibeyFinMenuItem(Strings.removeDownloadsOf(state.seriesDownloads.size), Icons.Default.Delete) {
                            menu = false
                            downloads.onRemoveSeries()
                        }
                    }
                } else {
                    state.download?.actions?.forEach { action ->
                        DownloadActionMenuItem(action) {
                            menu = false
                            downloads.onAction(item.id, action)
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun painterVector(id: Int) = ImageVector.vectorResource(id)

private const val ACTION_PLACES = 4

@Composable
private fun ActionRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val total = constraints.maxWidth
        val widest = measurables.maxOfOrNull { it.maxIntrinsicWidth(constraints.maxHeight) } ?: 0
        val place = minOf(widest, total / ACTION_PLACES)
        val gap = (total - place * ACTION_PLACES) / (ACTION_PLACES - 1)
        val placeables = measurables.map { it.measure(Constraints.fixedWidth(place)) }
        val height = placeables.maxOfOrNull { it.height } ?: 0

        layout(total, height) {
            placeables.forEachIndexed { i, placeable -> placeable.placeRelative(i * (place + gap), 0) }
        }
    }
}

@Composable
private fun ActionButton(label: String, description: String, icon: @Composable (Modifier) -> Unit, popKey: Boolean? = null, menu: @Composable () -> Unit = {}, onClick: () -> Unit) {
    val pop = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }

    LaunchedEffect(popKey) {
        if (first) {
            first = false

            return@LaunchedEffect
        }

        if (popKey == null) return@LaunchedEffect

        pop.snapTo(0.6f)
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
    }

    Box {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s1, Alignment.CenterVertically),
            modifier = Modifier
                // A generous target: at least 64 wide and 72 tall, well over the 48 dp minimum; as wide as its place
                .defaultMinSize(minWidth = 64.dp, minHeight = 72.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(VibeyFinTokens.Radius.m3Md))
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description }
                .padding(vertical = VibeyFinTokens.Space.s2),
        ) {
            icon(
                Modifier.size(30.dp).graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                },
            )

            // Medium weight in the full ink: the labels read against the picture
            Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium), color = VibeyFinTokens.Colors.ink, maxLines = 1)
        }

        menu()
    }
}

@Composable
private fun TitleArt(item: MediaItem, centered: Boolean) {
    if (item.logoUrl != null) {
        AsyncImage(
            model = item.logoUrl,
            contentDescription = item.title,
            contentScale = ContentScale.Fit,
            alignment = if (centered) Alignment.Center else Alignment.CenterStart,
            modifier = Modifier.fillMaxWidth(if (centered) 0.7f else 0.9f).heightIn(max = 112.dp),
        )
    } else {
        Text(
            text = item.title,
            style = BrandDisplay.copy(fontSize = 32.sp, lineHeight = 36.sp),
            color = VibeyFinTokens.Colors.ink,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FactsLine(state: ItemUiState, centered: Boolean) {
    if (state.facts.isEmpty() && !state.watched && state.badge == null) return

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3, if (centered) Alignment.CenterHorizontally else Alignment.Start),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = Strings.factsLine(state.facts),
            style = MaterialTheme.typography.bodyMedium,
            color = VibeyFinTokens.Colors.inkMuted,
        )
        state.badge?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = VibeyFinTokens.Colors.inkMuted,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = VibeyFinTokens.Space.s2, vertical = VibeyFinTokens.Space.s1),
            )
        }

        // Watched is an icon, in ink: aqua never signals status
        if (state.watched) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = Strings.watched,
                tint = VibeyFinTokens.Colors.inkMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun Overview(text: String) {
    var expanded by remember { mutableStateOf(false) }
    var overflows by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = VibeyFinTokens.Colors.ink,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
        )

        if (overflows || expanded) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) Strings.less else Strings.more, color = VibeyFinTokens.Colors.inkMuted)
            }
        }
    }
}

@Composable
private fun PlayButtons(label: String, canStartOver: Boolean, onPlay: (startOver: Boolean) -> Unit, fullWidth: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3), modifier = if (fullWidth) Modifier.fillMaxWidth() else Modifier) {
        Button(onClick = { onPlay(false) }, modifier = (if (fullWidth) Modifier.weight(1f) else Modifier.widthIn(min = 200.dp)).height(VibeyFinTokens.Size.buttonLarge)) {
            Icon(painter = painterResource(R.drawable.ic_play), contentDescription = null)
            Spacer(Modifier.width(VibeyFinTokens.Space.s2))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }

        if (canStartOver) {
            FilledTonalIconButton(onClick = { onPlay(true) }, modifier = Modifier.size(VibeyFinTokens.Size.buttonLarge)) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = Strings.startOver)
            }
        }
    }
}
