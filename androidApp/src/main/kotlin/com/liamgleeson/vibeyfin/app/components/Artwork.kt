package com.liamgleeson.vibeyfin.app.components

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import com.liamgleeson.vibeyfin.app.theme.VibeyFinTokens
import com.liamgleeson.vibeyfin.domain.model.MediaItem
import com.liamgleeson.vibeyfin.ui.common.Strings

/**
 * Artwork at [aspectRatio], or the title on a plain `surface-2` tile when there is none. The tile's
 * own colour shows through while an image loads or if it fails. Give [description] when the title
 * isn't written next to the image, so a screen reader still has a name for it.
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
        modifier = modifier.aspectRatio(aspectRatio).background(VibeyFinTokens.Colors.surface2),
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

/** A poster or a library tile with its title underneath, unless the picture already says it. */
@Composable
fun ArtworkTile(item: MediaItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
                modifier = Modifier.fillMaxWidth(),
            )
            if (item.played) WatchedBadge(Modifier.align(Alignment.TopEnd).padding(VibeyFinTokens.Space.s2))
        }
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
