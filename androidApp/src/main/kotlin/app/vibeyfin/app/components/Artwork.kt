package app.vibeyfin.app.components

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.app.theme.animationsEnabled
import app.vibeyfin.domain.model.MediaItem
import app.vibeyfin.ui.common.BlurHash
import app.vibeyfin.ui.common.Strings

/**
 * Artwork at [aspectRatio], or the title on a plain `surface-2` tile when there is none. While the image
 * loads, or if it fails, the tile shows [blurHash] (the server's blurred stand-in for the picture) and the
 * picture fades in over it; without a hash the tile's own colour shows through. Give [description] when
 * the title isn't written next to the image, so a screen reader still has a name for it.
 */
@Composable
fun Artwork(
    url: String?,
    title: String,
    aspectRatio: Float,
    modifier: Modifier = Modifier,
    description: String? = null,
    blurHash: String? = null,
) {
    Box(
        modifier = modifier.aspectRatio(aspectRatio).background(VibeyFinTokens.Colors.surface2),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            val blurred = rememberBlurHashPainter(blurHash, aspectRatio)
            val context = LocalPlatformContext.current
            val fadeMs = if (animationsEnabled()) VibeyFinTokens.Motion.baseMs else 0
            val request = remember(context, url, fadeMs) {
                ImageRequest.Builder(context).data(url).crossfade(fadeMs).build()
            }
            AsyncImage(
                model = request,
                contentDescription = description,
                contentScale = ContentScale.Crop,
                placeholder = blurred,
                // A failed picture keeps the blur it was already showing rather than going flat.
                error = blurred,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(VibeyFinTokens.Space.s2),
            )
        }
    }
}

/** The blurred picture for [blurHash], decoded once per hash at the shape of the tile; null when there is none or it is unusable. */
@Composable
private fun rememberBlurHashPainter(blurHash: String?, aspectRatio: Float): Painter? =
    remember(blurHash, aspectRatio) {
        blurHash?.let { BlurHash.decode(it, aspectRatio) }?.let { image ->
            val bitmap = Bitmap.createBitmap(image.pixels, image.width, image.height, Bitmap.Config.ARGB_8888)
            BitmapPainter(bitmap.asImageBitmap())
        }
    }

/** The check on a watched poster's top-right corner, on the scrim so it reads over any picture. */
@Composable
fun WatchedBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(24.dp).clip(CircleShape).background(VibeyFinTokens.Colors.scrim),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = Strings.watched,
            tint = VibeyFinTokens.Colors.ink,
            modifier = Modifier.size(16.dp),
        )
    }
}

/**
 * A poster or a library tile with its title underneath, unless the picture already says it. In a row that scrolls sideways
 * pass [captionLines]: the caption then always takes that many lines, so a long title in one tile never makes the row taller
 * (and pushes everything under it down) when it scrolls into view.
 */
@Composable
fun ArtworkTile(item: MediaItem, onClick: () -> Unit, modifier: Modifier = Modifier, captionLines: Int = 0) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(VibeyFinTokens.Radius.artwork))
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = Strings.cardLabel(item.title, item.year) },
        verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2),
    ) {
        Box(Modifier.clip(RoundedCornerShape(VibeyFinTokens.Radius.artwork))) {
            Artwork(
                url = item.posterUrl,
                title = item.title,
                aspectRatio = item.posterAspectRatio,
                blurHash = item.posterBlurHash,
                modifier = Modifier.fillMaxWidth(),
            )
            if (item.played) WatchedBadge(Modifier.align(Alignment.TopEnd).padding(VibeyFinTokens.Space.s2))
        }
        if (!item.nameIsOnPoster || captionLines > 0) {
            Text(
                // A tile whose picture already says its name keeps the same empty space, so the row stays one height.
                text = if (item.nameIsOnPoster) "" else item.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
                minLines = if (captionLines > 0) captionLines else 1,
                maxLines = if (captionLines > 0) captionLines else 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
