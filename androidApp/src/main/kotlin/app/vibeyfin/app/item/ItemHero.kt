package app.vibeyfin.app.item

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vibeyfin.R
import app.vibeyfin.app.components.Artwork
import app.vibeyfin.app.theme.BrandDisplay
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.domain.model.MediaItem
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.item.ItemUiState
import app.vibeyfin.ui.item.Trailer
import coil3.compose.AsyncImage

/** Phones: the backdrop is cropped to 2:3, so the picture fills most of the first screen and runs under the status bar. */
private const val HERO_ASPECT = 2f / 3f

/** Without a picture the hero is this much shorter. */
private const val HERO_ASPECT_NO_PICTURE = 1.1f

/**
 * The phone's hero: the backdrop full-bleed with the title, the facts, the overview and Play laid over it, centred. The text sits
 * on the scrim: dark where the text is, clear where the picture should read, ending in the page's own colour. (A tablet lays
 * the page out differently: see [ItemBackdrop] and [HeroDetails].)
 */
@Composable
fun ItemHero(
    item: MediaItem,
    state: ItemUiState,
    margin: Dp,
    onPlay: (startOver: Boolean) -> Unit,
    onOpenSeries: (String) -> Unit,
    onToggleFavorite: () -> Unit,
    onPlayTrailer: (String) -> Unit,
    onTogglePlayed: () -> Unit,
) {
    Box(Modifier.fillMaxWidth()) {
        val picture = item.backdropUrl ?: item.posterUrl
        if (picture != null) {
            Artwork(
                url = picture,
                title = item.title,
                aspectRatio = HERO_ASPECT,
                blurHash = if (item.backdropUrl != null) item.backdropBlurHash else item.posterBlurHash,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            // Nothing to show behind the title: a plain tile, shorter than a picture would be, without the title written twice.
            Box(Modifier.fillMaxWidth().aspectRatio(HERO_ASPECT_NO_PICTURE).background(VibeyFinTokens.Colors.surface2))
        }
        val scrim = VibeyFinTokens.Colors.scrim
        val scrimStrong = VibeyFinTokens.Colors.scrimStrong
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(0f to scrim, 0.18f to Color.Transparent, 0.40f to Color.Transparent, 0.60f to scrim, 0.82f to scrimStrong, 1f to VibeyFinTokens.Colors.bg),
            ),
        )
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = margin).padding(bottom = VibeyFinTokens.Space.s4),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3),
        ) {
            HeroDetails(item, state, centered = true, onPlay, onOpenSeries, onToggleFavorite, onPlayTrailer, onTogglePlayed)
        }
    }
}

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
        // The darkening: from the picture as it is to nearly the page's own colour, as the page scrolls up over it.
        Box(Modifier.matchParentSize().background(VibeyFinTokens.Colors.bg.copy(alpha = MAX_DARKEN * progress.coerceIn(0f, 1f))))
    }
}

/**
 * A phone's picture: pinned at the top, [height] tall, and shrinking from the bottom as the page scrolls, faster than the text
 * moves, so the page's content comes up without scrolling past all of it. A scrim under the text fades it into the page.
 */
@Composable
fun CollapsingBackdrop(item: MediaItem, height: Dp, scroll: androidx.compose.foundation.ScrollState) {
    val fullPx = with(androidx.compose.ui.platform.LocalDensity.current) { height.roundToPx() }
    Box(
        Modifier
            .fillMaxWidth()
            // Read in the layout step, so scrolling re-measures this box and nothing else.
            .layout { measurable, constraints ->
                val shown = (fullPx - (scroll.value * COLLAPSE_RATE).toInt()).coerceIn(0, fullPx)
                val placeable = measurable.measure(constraints.copy(minHeight = shown, maxHeight = shown))
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
            .clipToBounds(),
    ) {
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
    }
}

/** The picture shrinks this many times faster than the page scrolls. */
private const val COLLAPSE_RATE = 1.7f

/** How dark the backdrop gets once the page has scrolled a screen: nearly the page's own colour, with a hint of the picture left. */
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
) {
    Column(
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3),
        modifier = Modifier.fillMaxWidth(),
    ) {
        // An episode or a season leads back to its show.
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
                text = item.genres.joinToString("  ·  "),
                style = MaterialTheme.typography.bodyMedium,
                color = VibeyFinTokens.Colors.inkMuted,
                textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            )
        }
        item.overview?.let { Overview(it) }
        if (state.canPlay) PlayButtons(state.playLabel, state.canStartOver, onPlay, fullWidth = true)
        ActionButtons(state, centered = centered, onToggleFavorite, onPlayTrailer, onTogglePlayed)
    }
}

/**
 * The actions under Play, each an icon over a label: My List (the server's favourites), Trailer (only when there is one) and
 * Download (it does nothing yet). A trailer the server holds plays in the app; one on the web opens in the system's viewer.
 */
@Composable
private fun ActionButtons(state: ItemUiState, centered: Boolean, onToggleFavorite: () -> Unit, onPlayTrailer: (String) -> Unit, onTogglePlayed: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    // Four equal columns, as on other streaming apps: My List, Trailer, Watched, Download. A missing trailer leaves its column empty.
    // Only the actions that exist are drawn, so three share the width as evenly as four. The first starts at the left edge of Play and the last ends at its right edge, with the rest spread evenly between.
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        ActionButton(
            centered = centered,
            label = Strings.myList,
            description = Strings.myListAction(state.isFavorite),
            icon = rememberVectorPainter(if (state.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder),
            popKey = state.isFavorite,
            onClick = onToggleFavorite,
        )
        val trailer = state.trailer
        if (trailer != null) {
            ActionButton(label = Strings.trailer, description = Strings.trailer, icon = painterResource(R.drawable.ic_trailer), centered = centered) {
                when (trailer) {
                    is Trailer.Local -> onPlayTrailer(trailer.itemId)
                    is Trailer.Remote -> uriHandler.openUri(trailer.url)
                }
            }
        }
        if (state.canMark) {
            ActionButton(
                centered = centered,
                label = Strings.watchedAction,
                description = state.markLabel,
                icon = rememberVectorPainter(if (state.watched) Icons.Default.CheckCircle else Icons.Outlined.CheckCircle),
                popKey = state.watched,
                onClick = onTogglePlayed,
            )
        }
        // Offline is a later phase: the button is here, and does nothing yet.
        ActionButton(label = Strings.download, description = Strings.download, icon = painterResource(R.drawable.ic_download), centered = centered) {}
    }
}

/** One action: the icon over its label, in a quarter of the row. */
@Composable
private fun RowScope.ActionButton(label: String, description: String, icon: androidx.compose.ui.graphics.painter.Painter, centered: Boolean, popKey: Boolean? = null, onClick: () -> Unit) {
    // A toggle springs when it flips: it swells, then settles with a little bounce.
    val pop = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(popKey) {
        if (first) { first = false; return@LaunchedEffect }
        if (popKey == null) return@LaunchedEffect
        pop.snapTo(0.6f)
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s1),
        modifier = Modifier
            .defaultMinSize(minWidth = 56.dp)
            .clip(RoundedCornerShape(VibeyFinTokens.Radius.m3Md))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(vertical = VibeyFinTokens.Space.s2),
    ) {
        Icon(painter = icon, contentDescription = null, tint = VibeyFinTokens.Colors.ink, modifier = Modifier.size(30.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value })
        Text(label, style = MaterialTheme.typography.labelLarge, color = VibeyFinTokens.Colors.inkMuted, maxLines = 1)
    }
}

/** The title as its `Logo` image, centred and large; the `brand-display` title when the item has none. */
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

/** Year, rating, runtime (or seasons): centred under the title, with the picture badge and a check once it is watched. */
@Composable
private fun FactsLine(state: ItemUiState, centered: Boolean) {
    if (state.facts.isEmpty() && !state.watched && state.badge == null) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3, if (centered) Alignment.CenterHorizontally else Alignment.Start),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = state.facts.joinToString("  ·  "),
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
        // Watched is an icon, in ink: aqua never signals status.
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

/** Three lines of the overview and "More" when there is more; "Less" folds it back. */
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

/**
 * The one filled action on the page: aqua, 56 dp tall, reading "Play", "Resume · 38m left" or for a show "Play S2 E4". "Start
 * over" sits beside it as a round icon-only secondary button (a circular arrow), only when there is something to resume.
 */
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
