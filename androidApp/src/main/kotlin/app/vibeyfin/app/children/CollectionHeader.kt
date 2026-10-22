package app.vibeyfin.app.children

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.sp
import app.vibeyfin.app.components.Artwork
import app.vibeyfin.app.theme.BrandDisplay
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.domain.model.MediaItem
import app.vibeyfin.ui.common.Strings

private const val COLLECTION_HERO_ASPECT = 3f / 4f
private const val COLLECTION_HERO_ASPECT_WIDE = 21f / 9f

/** From this width the header is the tablet's: a wide picture with the details in a column on the left. */
private const val WIDE_FROM_DP = 600

/**
 * A collection's header: its picture full-bleed under the status bar, fading into the page, with the title, how many titles it
 * holds and the overview centred over the lower half. The members follow below as a poster grid.
 */
@Composable
fun CollectionHeader(title: String, collection: MediaItem?, facts: List<String>, margin: Dp, imageHint: String? = null) {
    var expanded by remember { mutableStateOf(false) }
    val wide = LocalConfiguration.current.screenWidthDp >= WIDE_FROM_DP
    val aspect = if (wide) COLLECTION_HERO_ASPECT_WIDE else COLLECTION_HERO_ASPECT
    Box(Modifier.fillMaxWidth()) {
        // The collection's own picture once it is read; until then the one from the tile or result that was opened.
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
            // A scrim from the left behind the column of text.
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
        Column(
            modifier = Modifier
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
                    text = facts.joinToString("  \u00B7  "),
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
