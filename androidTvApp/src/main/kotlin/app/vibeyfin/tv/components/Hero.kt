package app.vibeyfin.tv.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.vibeyfin.design.VibeyFinTokens
import app.vibeyfin.domain.model.MediaItem
import app.vibeyfin.design.BrandDisplay
import app.vibeyfin.tv.theme.TvMarginHorizontal
import app.vibeyfin.ui.common.Strings
import coil3.compose.AsyncImage

@Composable
fun HeroBillboard(
    item: MediaItem,
    onPlay: () -> Unit,
    onMyList: () -> Unit,
    playModifier: Modifier = Modifier,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().height(380.dp)) {
        AsyncImage(
            model = item.backdropUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().drawWithCache {
                val toStart = Brush.horizontalGradient(listOf(VibeyFinTokens.Colors.bg, Color.Transparent), startX = 0f, endX = size.width * 0.75f)
                val toBottom = Brush.verticalGradient(listOf(Color.Transparent, VibeyFinTokens.Colors.bg), startY = size.height * 0.5f, endY = size.height)
                onDrawWithContent {
                    drawContent()
                    drawRect(toStart)
                    drawRect(toBottom)
                }
            },
        )
        Column(
            Modifier.align(Alignment.BottomStart).padding(start = TvMarginHorizontal, bottom = 24.dp).widthIn(max = 560.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // The logo when it loads, else the title, so the hero always has a name.
            var logoFailed by remember(item.logoUrl) { mutableStateOf(false) }
            if (item.logoUrl != null && !logoFailed) {
                AsyncImage(
                    item.logoUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                    onError = { logoFailed = true },
                    modifier = Modifier.height(72.dp).widthIn(max = 320.dp),
                )
            } else {
                Text(item.title, style = BrandDisplay, color = MaterialTheme.colorScheme.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            val meta = listOfNotNull(item.year?.toString(), item.ageRating, item.runtimeMinutes?.let { "${it / 60}h ${it % 60}m".removePrefix("0h ") }, item.genres.take(2).joinToString(", ").ifBlank { null })
            if (meta.isNotEmpty()) {
                Text(meta.joinToString("  ·  "), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item.overview?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 6.dp)) {
                PrimaryAction(Strings.play, onPlay, playModifier)
                SecondaryAction(Strings.myListAction(item.isFavorite), onMyList)
            }
        }
    }
}

@Composable
fun HeroPlaceholder() {
    Box(Modifier.fillMaxWidth().height(380.dp).background(VibeyFinTokens.Colors.surface1))
}
