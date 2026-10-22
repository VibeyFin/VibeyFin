package app.vibeyfin.app.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.vibeyfin.R
import app.vibeyfin.app.components.Artwork
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.domain.model.MediaItem
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.home.heroFacts
import coil3.compose.AsyncImage

/** The card is 4:3 on a phone and wider on a tablet, a banner rather than a full poster, so Continue watching stays close to the top. */
private const val HERO_ASPECT = 4f / 3f
private const val HERO_ASPECT_WIDE = 2.6f

/** From this screen width the hero is the tablet's: wider, with its text at the start. */
private const val HERO_WIDE_FROM_DP = 600

private val HERO_BUTTON_HEIGHT = 44.dp

/**
 * The one featured item, above Continue watching, as a rounded card inside the page's margins: its backdrop under a scrim, the Logo
 * as the title, a line saying what it is and its genres, then Play (the one aqua button) beside My List. Tapping the picture opens the
 * item's page. Play plays: a movie, or the episode a show is up to.
 */
@Composable
fun HomeHeroSection(
    item: MediaItem,
    wide: Boolean,
    onPlay: () -> Unit,
    onOpen: (String) -> Unit,
    onMyList: () -> Unit,
) {
    val shape = RoundedCornerShape(VibeyFinTokens.Radius.m3Md)
    Box(
        Modifier
            // The grid around it already has the page margin, so the card runs from margin to margin.
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, VibeyFinTokens.Colors.surface3, shape)
            .clickable { onOpen(item.id) }
            .semantics { contentDescription = Strings.featured(item.title) },
    ) {
        Artwork(
            url = item.backdropUrl,
            title = item.title,
            aspectRatio = if (wide) HERO_ASPECT_WIDE else HERO_ASPECT,
            blurHash = item.backdropBlurHash,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(0f to Color.Transparent, 0.4f to Color.Transparent, 0.75f to VibeyFinTokens.Colors.scrim, 1f to VibeyFinTokens.Colors.scrimStrong),
            ),
        )
        val align = if (wide) Alignment.Start else Alignment.CenterHorizontally
        Column(
            Modifier.align(if (wide) Alignment.BottomStart else Alignment.BottomCenter).fillMaxWidth().padding(horizontal = VibeyFinTokens.Space.s3).padding(bottom = VibeyFinTokens.Space.s3),
            horizontalAlignment = align,
            verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2),
        ) {
            AsyncImage(
                model = item.logoUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                alignment = if (wide) Alignment.CenterStart else Alignment.Center,
                modifier = Modifier.fillMaxWidth(if (wide) 0.3f else 0.6f).heightIn(max = if (wide) 72.dp else 52.dp),
            )
            val facts = item.heroFacts
            if (facts.isNotEmpty()) {
                // "Show · Offbeat · Heartfelt · Comedy": what it is, then its genres.
                Text(
                    facts.joinToString("  ·  "),
                    style = MaterialTheme.typography.labelLarge,
                    color = VibeyFinTokens.Colors.ink,
                    textAlign = if (wide) TextAlign.Start else TextAlign.Center,
                    maxLines = 1,
                )
            }
            Row(Modifier.then(if (wide) Modifier.width(360.dp) else Modifier.fillMaxWidth()), horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2)) {
                Button(
                    onClick = onPlay,
                    shape = RoundedCornerShape(VibeyFinTokens.Radius.m3Md),
                    modifier = Modifier.weight(1f).height(HERO_BUTTON_HEIGHT),
                ) {
                    Icon(painter = painterResource(R.drawable.ic_play), contentDescription = null)
                    Spacer(Modifier.width(VibeyFinTokens.Space.s2))
                    Text(Strings.play, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
                // Not aqua: aqua is the one primary action. A soft, see-through button over the picture.
                Button(
                    onClick = onMyList,
                    shape = RoundedCornerShape(VibeyFinTokens.Radius.m3Md),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f), contentColor = VibeyFinTokens.Colors.ink),
                    modifier = Modifier.weight(1f).height(HERO_BUTTON_HEIGHT).semantics { contentDescription = Strings.myListAction(item.isFavorite) },
                ) {
                    Icon(if (item.isFavorite) Icons.Filled.Check else Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(VibeyFinTokens.Space.s2))
                    Text(Strings.myList, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium), maxLines = 1)
                }
            }
        }
    }
}
