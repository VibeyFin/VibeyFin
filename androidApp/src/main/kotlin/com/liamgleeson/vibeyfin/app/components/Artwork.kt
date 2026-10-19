package com.liamgleeson.vibeyfin.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.liamgleeson.vibeyfin.app.theme.VibeyFinPalette
import com.liamgleeson.vibeyfin.domain.model.MediaItem

/**
 * Artwork at [aspectRatio], or the first letter of [title] on a plain tile when there is none. The
 * tile's own colour shows through while an image loads or if it fails. Give [description] when the
 * title isn't written next to the image, so a screen reader still has a name for it.
 */
@Composable
fun Artwork(
    url: String?,
    title: String,
    aspectRatio: Float,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    Box(
        modifier = modifier.aspectRatio(aspectRatio).background(VibeyFinPalette.SurfaceHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = description,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = title.take(1).uppercase(),
                style = MaterialTheme.typography.headlineMedium,
                color = VibeyFinPalette.TextMuted,
            )
        }
    }
}

/** A poster or a library tile with its title underneath, unless the picture already says it. */
@Composable
fun ArtworkTile(item: MediaItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Artwork(
            url = item.posterUrl,
            title = item.title,
            aspectRatio = item.posterAspectRatio,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
            description = item.title.takeIf { item.nameIsOnPoster },
        )
        if (!item.nameIsOnPoster) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
