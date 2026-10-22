package app.vibeyfin.app.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.domain.model.MediaItem
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.home.MediaRow
import app.vibeyfin.ui.home.SeeAll

/** A poster's title always takes this many lines in a row, so the row keeps one height as it scrolls. */
const val POSTER_CAPTION_LINES = 2

@Composable
fun SectionTitle(text: String, maxLines: Int = Int.MAX_VALUE, modifier: Modifier = Modifier) {
    Text(text = text, modifier = modifier, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}


/** Lets a child use [by] more width on each side than its parent gives it, to run under the parent's padding. */
fun Modifier.bleed(by: Dp) = layout { measurable, constraints ->
    val extra = by.roundToPx() * 2
    val width = constraints.maxWidth + extra
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
}


enum class RowPhase { Loading, Failed, Ready }

/** The fade from a row's grey posters to its content. */
const val ROW_FADE_MS = 140


/** A row of posters under a header with "See all": loading is grey posters, a failure says why with Try again. */
@Composable
fun MediaRowSection(
    row: MediaRow,
    margin: Dp,
    onOpen: (MediaItem) -> Unit,
    onSeeAll: ((SeeAll) -> Unit)?,
    onRetry: () -> Unit,
    // Posters are 112 on a phone and larger on a tablet (`screens.md`).
    tileWidth: Dp = VibeyFinTokens.Size.posterWidth,
) {
    Column(verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            // One line, so a long library name never makes the header taller.
            Box(Modifier.weight(1f)) { SectionTitle(row.title, maxLines = 1) }
            val target = row.seeAll
            if (target != null && onSeeAll != null && row.items.isNotEmpty()) {
                TextButton(onClick = { onSeeAll(target) }, modifier = Modifier.semantics { contentDescription = Strings.seeAllIn(row.title) }) {
                    Text(Strings.seeAll, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        // The grey posters give way to the real ones with a short fade, and the row grows or shrinks smoothly if its
        // height differs.
        // Without repeats (the list keys must be unique), worked out once per list, not on every redraw.
        val tiles = remember(row.items) { row.items.distinctBy { it.id } }
        val phase = if (row.loading) RowPhase.Loading else if (row.message != null) RowPhase.Failed else RowPhase.Ready
        Crossfade(targetState = phase, animationSpec = tween(ROW_FADE_MS), modifier = Modifier.animateContentSize(tween(ROW_FADE_MS))) { shown ->
            when (shown) {
                RowPhase.Loading -> PosterSkeletonRow(Modifier.bleed(margin).padding(horizontal = margin), tileWidth = tileWidth)
                RowPhase.Failed -> LoadError(row.message ?: "", onRetry)
                RowPhase.Ready -> LazyRow(
                    modifier = Modifier.bleed(margin),
                    contentPadding = PaddingValues(horizontal = margin),
                    horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3),
                ) {
                    items(tiles, key = { it.id }, contentType = { "poster" }) { item ->
                        ArtworkTile(item, onClick = { onOpen(item) }, modifier = Modifier.width(tileWidth), captionLines = POSTER_CAPTION_LINES)
                    }
                }
            }
        }
    }
}


