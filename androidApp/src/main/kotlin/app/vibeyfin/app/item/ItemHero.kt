package app.vibeyfin.app.item

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
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
import coil3.compose.AsyncImage

/** Phones: the backdrop is cropped to 2:3, so the picture fills most of the first screen and runs under the status bar. */
private const val HERO_ASPECT = 2f / 3f

/** Tablets and unfolded foldables show the backdrop as it is, 16:9. */
private const val HERO_ASPECT_WIDE = 16f / 9f

/** Without a picture the hero is this much shorter. */
private const val HERO_ASPECT_NO_PICTURE = 1.1f

/**
 * The page's hero. On a phone: the backdrop full-bleed with the title, the facts, the overview and Play laid over it,
 * centred. On a tablet or an unfolded foldable ([wide]): the backdrop at its own 16:9 with the details in a left column over
 * it. Either way the text sits on the scrim: dark where the text is, clear where the picture should read, ending in the
 * page's own colour.
 */
@Composable
fun ItemHero(item: MediaItem, state: ItemUiState, wide: Boolean, margin: Dp, onPlay: (startOver: Boolean) -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        val picture = item.backdropUrl ?: item.posterUrl
        val aspect = if (wide) HERO_ASPECT_WIDE else HERO_ASPECT
        if (picture != null) {
            Artwork(
                url = picture,
                title = item.title,
                aspectRatio = aspect,
                blurHash = if (item.backdropUrl != null) item.backdropBlurHash else item.posterBlurHash,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            // Nothing to show behind the title: a plain tile, shorter than a picture would be, without the title written twice.
            Box(Modifier.fillMaxWidth().aspectRatio(if (wide) HERO_ASPECT_WIDE else HERO_ASPECT_NO_PICTURE).background(VibeyFinTokens.Colors.surface2))
        }
        // The same scrim top and bottom on both; a tablet adds one from the left, behind its column of text.
        val scrim = VibeyFinTokens.Colors.scrim
        val scrimStrong = VibeyFinTokens.Colors.scrimStrong
        val stops = if (wide) {
            arrayOf(0f to scrim, 0.18f to Color.Transparent, 0.75f to Color.Transparent, 0.92f to scrim, 0.97f to scrimStrong, 1f to VibeyFinTokens.Colors.bg)
        } else {
            arrayOf(0f to scrim, 0.18f to Color.Transparent, 0.40f to Color.Transparent, 0.60f to scrim, 0.82f to scrimStrong, 1f to VibeyFinTokens.Colors.bg)
        }
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(*stops)))
        if (wide) {
            Box(
                Modifier.matchParentSize().background(
                    Brush.horizontalGradient(
                        0f to VibeyFinTokens.Colors.scrimStrong,
                        0.3f to VibeyFinTokens.Colors.scrim,
                        0.6f to Color.Transparent,
                    ),
                ),
            )
        }
        val alignment = if (wide) Alignment.Start else Alignment.CenterHorizontally
        Column(
            modifier = (if (wide) Modifier.align(Alignment.BottomStart).fillMaxWidth(0.46f).widthIn(max = 520.dp) else Modifier.align(Alignment.BottomCenter).fillMaxWidth())
                .padding(horizontal = margin)
                .padding(bottom = VibeyFinTokens.Space.s4),
            horizontalAlignment = alignment,
            verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3),
        ) {
            item.seriesName?.let {
                Text(it, style = MaterialTheme.typography.labelLarge, color = VibeyFinTokens.Colors.inkMuted)
            }
            TitleArt(item, centered = !wide)
            FactsLine(state, centered = !wide)
            if (item.genres.isNotEmpty()) {
                Text(
                    text = item.genres.joinToString("  ·  "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = VibeyFinTokens.Colors.inkMuted,
                    textAlign = if (wide) TextAlign.Start else TextAlign.Center,
                )
            }
            item.overview?.let { Overview(it) }
            if (state.canPlay) PlayButtons(state.playLabel, state.canStartOver, onPlay, fullWidth = !wide)
        }
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
        horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2, if (centered) Alignment.CenterHorizontally else Alignment.Start),
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
