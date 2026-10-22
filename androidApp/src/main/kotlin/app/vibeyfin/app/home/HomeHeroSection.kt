package app.vibeyfin.app.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.vibeyfin.R
import app.vibeyfin.app.components.Artwork
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.domain.model.ItemKind
import app.vibeyfin.domain.model.MediaItem
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.home.heroFacts
import coil3.compose.AsyncImage

/** The hero is a 3:2 crop of the backdrop on a phone: a banner, so Continue watching stays close to the top. */
private const val HERO_ASPECT = 3f / 2f

/**
 * The one featured item, above Continue watching: its backdrop full width under a scrim, the Logo as the title, a line of facts
 * and Play, the one aqua button. Tapping the picture opens the item's page. A movie plays at once; a show opens its page, where
 * Play knows which episode is next.
 */
@Composable
fun HomeHeroSection(item: MediaItem, margin: Dp, onPlay: (String) -> Unit, onOpen: (String) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clickable { onOpen(item.id) }
            .semantics { contentDescription = Strings.featured(item.title) },
    ) {
        Artwork(
            url = item.backdropUrl,
            title = item.title,
            aspectRatio = HERO_ASPECT,
            blurHash = item.backdropBlurHash,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(0f to Color.Transparent, 0.45f to Color.Transparent, 0.8f to VibeyFinTokens.Colors.scrim, 1f to VibeyFinTokens.Colors.scrimStrong),
            ),
        )
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = margin).padding(bottom = VibeyFinTokens.Space.s4),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2),
        ) {
            AsyncImage(
                model = item.logoUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth(0.6f).heightIn(max = 56.dp),
            )
            val facts = item.heroFacts
            if (facts.isNotEmpty()) {
                Text(facts.joinToString("  ·  "), style = MaterialTheme.typography.labelLarge, color = VibeyFinTokens.Colors.ink, textAlign = TextAlign.Center)
            }
            Button(
                onClick = { if (item.kind == ItemKind.Movie) onPlay(item.id) else onOpen(item.id) },
                modifier = Modifier.heightIn(min = VibeyFinTokens.Size.buttonLarge).width(180.dp),
            ) {
                Icon(painter = painterResource(R.drawable.ic_play), contentDescription = null)
                Spacer(Modifier.width(VibeyFinTokens.Space.s2))
                Text(Strings.play, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}
