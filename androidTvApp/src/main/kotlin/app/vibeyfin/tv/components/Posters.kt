package app.vibeyfin.tv.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.vibeyfin.design.VibeyFinTokens
import app.vibeyfin.domain.model.MediaItem
import app.vibeyfin.tv.theme.TvMarginHorizontal
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.home.ContinueCard
import app.vibeyfin.ui.home.MediaRow
import coil3.compose.AsyncImage

private val PosterWidth = 150.dp
private val ContinueWidth = 280.dp

private val RowPadding = PaddingValues(horizontal = TvMarginHorizontal, vertical = 8.dp)

@Composable
fun RowTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = TvMarginHorizontal),
    )
}

@Composable
fun PosterRow(
    row: MediaRow,
    onOpen: (MediaItem) -> Unit,
    onRetry: () -> Unit,
    tileWidth: Dp = PosterWidth,
    aspect: Float = 2f / 3f,
    // A library's picture already has its name on it.
    captions: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RowTitle(row.title)
        when {
            row.loading -> SkeletonRow(aspect = aspect, width = tileWidth)
            row.message != null -> RowError(row.message!!, onRetry)
            else -> {
                val items = remember(row.items) { row.items.distinctBy { it.id } }
                LazyRow(contentPadding = RowPadding, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(items, key = { it.id }, contentType = { "poster" }) { item ->
                        Column(Modifier.width(tileWidth)) {
                            FocusSurface(onClick = { onOpen(item) }, modifier = Modifier.fillMaxWidth().aspectRatio(aspect)) {
                                if (item.posterUrl != null) {
                                    AsyncImage(item.posterUrl, contentDescription = item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                } else {
                                    Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
                                        Text(item.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                            if (captions) {
                                Text(
                                    item.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ContinueRowSection(title: String, cards: List<ContinueCard>, onPlay: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RowTitle(title)
        LazyRow(contentPadding = RowPadding, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(cards, key = { it.id }, contentType = { "continue" }) { card ->
                Column(Modifier.width(ContinueWidth)) {
                    FocusSurface(onClick = { onPlay(card.id) }, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                        AsyncImage(card.imageUrl, contentDescription = card.accessibilityLabel, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        card.progress?.let { progress ->
                            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(VibeyFinTokens.Colors.progressTrack)) {
                                Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(VibeyFinTokens.Colors.accent))
                            }
                        }
                    }
                    Text(card.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                    card.subtitle?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
fun SkeletonRow(aspect: Float, width: Dp) {
    LazyRow(contentPadding = RowPadding, horizontalArrangement = Arrangement.spacedBy(16.dp), userScrollEnabled = false) {
        items(8) {
            Box(Modifier.width(width).aspectRatio(aspect).clip(RoundedCornerShape(12.dp)).background(VibeyFinTokens.Colors.surface2))
        }
    }
}

@Composable
fun RowError(message: String, onRetry: () -> Unit) {
    Column(Modifier.padding(horizontal = TvMarginHorizontal, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        SecondaryAction(Strings.tryAgain, onRetry)
    }
}
