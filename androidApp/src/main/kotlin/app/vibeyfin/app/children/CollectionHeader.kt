package app.vibeyfin.app.children

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vibeyfin.app.components.Artwork
import app.vibeyfin.app.item.scrollDarkening
import app.vibeyfin.app.theme.BrandDisplay
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.domain.model.MediaItem
import app.vibeyfin.ui.common.Strings

private const val COLLECTION_HERO_ASPECT = 3f / 4f
private const val COLLECTION_HERO_ASPECT_WIDE = 21f / 9f

private const val WIDE_FROM_DP = 600

/**
 * A collection's header: its picture full-bleed under the status bar, fading into the page, with the title, how many titles it
 * holds and the overview centered over the lower half. The members follow below as a poster grid.
 *
 * As on an item's page, the picture stays put while the page scrolls ([scrolled], in pixels): the text goes up over it, it darkens,
 * and the rest of the page covers it from below.
 */
@Composable
fun CollectionHeader(title: String, collection: MediaItem?, facts: List<String>, margin: Dp, imageHint: String? = null, scrolled: () -> Int = { 0 }) {
    var expanded by remember { mutableStateOf(false) }
    val wide = LocalConfiguration.current.screenWidthDp >= WIDE_FROM_DP
    val aspect = if (wide) COLLECTION_HERO_ASPECT_WIDE else COLLECTION_HERO_ASPECT

    Box(Modifier.fillMaxWidth().clipToBounds()) {
        // Moved down as far as the header has gone up, so it holds still; the clip lets the page below cover it
        Box(Modifier.fillMaxWidth().graphicsLayer { translationY = scrolled().toFloat() }) {
            CollectionBackdrop(title, collection, imageHint, aspect, wide, scrolled)
        }

        // The title, facts and overview come in together once the collection is read, so the title never jumps as the rest arrives
        val textAlpha by animateFloatAsState(if (collection != null) 1f else 0f, tween(250), label = "header text")

        Column(
            modifier = Modifier
                .graphicsLayer { alpha = textAlpha }
                .align(if (wide) Alignment.BottomStart else Alignment.BottomCenter)
                .then(if (wide) Modifier.widthIn(max = 520.dp) else Modifier.fillMaxWidth())
                .padding(horizontal = margin)
                .padding(bottom = VibeyFinTokens.Space.s4),
            horizontalAlignment = if (wide) Alignment.Start else Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2),
        ) {
            Text(
                text = title,
                style = BrandDisplay.copy(fontSize = 34.sp, lineHeight = 38.sp),
                color = VibeyFinTokens.Colors.ink,
                textAlign = if (wide) TextAlign.Start else TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            if (facts.isNotEmpty()) {
                Text(
                    text = Strings.factsLine(facts),
                    style = MaterialTheme.typography.titleSmall,
                    color = VibeyFinTokens.Colors.inkMuted,
                    textAlign = if (wide) TextAlign.Start else TextAlign.Center,
                )
            }

            collection?.overview?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = VibeyFinTokens.Colors.ink,
                    textAlign = if (wide) TextAlign.Start else TextAlign.Center,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { expanded = !expanded }.animateContentSize(),
                )

                if (it.length > 140) {
                    Text(
                        text = if (expanded) Strings.less else Strings.more,
                        style = MaterialTheme.typography.labelLarge,
                        color = VibeyFinTokens.Colors.inkMuted,
                        modifier = Modifier.clickable { expanded = !expanded }.padding(vertical = VibeyFinTokens.Space.s1),
                    )
                }
            }
        }
    }
}

// The picture with its scrims: the collection's own once it is read, until then the one from the tile or result that was opened
@Composable
private fun BoxScope.CollectionBackdrop(title: String, collection: MediaItem?, imageHint: String?, aspect: Float, wide: Boolean, scrolled: () -> Int) {
    val picture = collection?.let { it.backdropUrl ?: it.posterUrl } ?: imageHint

    if (picture != null) {
        Artwork(
            url = picture,
            title = title,
            aspectRatio = aspect,
            blurHash = collection?.let { if (it.backdropUrl != null) it.backdropBlurHash else it.posterBlurHash },
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        Box(Modifier.fillMaxWidth().aspectRatio(aspect).background(VibeyFinTokens.Colors.surface2))
    }

    if (wide) {
        Box(
            Modifier.matchParentSize().background(
                Brush.horizontalGradient(0f to VibeyFinTokens.Colors.scrimStrong, 0.45f to VibeyFinTokens.Colors.scrim, 0.8f to Color.Transparent),
            ),
        )
    }

    Box(
        Modifier.matchParentSize().background(
            Brush.verticalGradient(
                0f to VibeyFinTokens.Colors.scrim,
                0.2f to Color.Transparent,
                0.45f to Color.Transparent,
                0.7f to VibeyFinTokens.Colors.scrim,
                0.88f to VibeyFinTokens.Colors.scrimStrong,
                1f to VibeyFinTokens.Colors.bg,
            ),
        ),
    )

    // The darkening as the page scrolls; read in the draw step, so scrolling redraws this layer and nothing else
    Box(Modifier.matchParentSize().graphicsLayer { alpha = scrollDarkening(scrolled().toFloat(), size.height) }.background(VibeyFinTokens.Colors.bg))
}
