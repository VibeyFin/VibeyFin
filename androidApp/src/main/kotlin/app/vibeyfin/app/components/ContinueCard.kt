package app.vibeyfin.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.vibeyfin.R
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.home.ContinueCard

private const val CARD_ASPECT = 16f / 9f
private val PROGRESS_HEIGHT = 3.dp
private val PLAY_DISC = 44.dp

/** Between the title and the line under it: close, so they read as one. */
private val CARD_LINE_GAP = 2.dp

/**
 * A 16:9 card in the Continue watching row: the picture with a play disc in the middle and the aqua
 * progress bar along its bottom edge if something is in progress, then the title with what is left (or
 * the episode) under it, close together, and a "…" button for the card's actions. Tapping the picture or
 * the words plays; there is no detail page in between. A long press opens the same actions.
 */
@Composable
fun ContinueCard(card: ContinueCard, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.width(VibeyFinTokens.Size.thumbWidth)) {
        Box(
            Modifier
                .clip(RoundedCornerShape(VibeyFinTokens.Radius.artwork))
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .semantics(mergeDescendants = true) { contentDescription = card.accessibilityLabel },
        ) {
            // Rounded again inside the outer clip: a pressed card shrinks, and its corners shrink with it.
            Box(Modifier.clip(RoundedCornerShape(VibeyFinTokens.Radius.artwork))) {
                Artwork(
                    url = card.imageUrl,
                    title = card.title,
                    aspectRatio = CARD_ASPECT,
                    blurHash = card.imageBlurHash,
                    modifier = Modifier.fillMaxWidth(),
                )
                PlayDisc(Modifier.align(Alignment.Center))
                card.progress?.let { ProgressBar(it, Modifier.align(Alignment.BottomStart)) }
            }
        }
        Row(verticalAlignment = Alignment.Top) {
            // The words play too; the picture already says so to a screen reader, so they stay out of its way.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = VibeyFinTokens.Space.s2)
                    .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                    .clearAndSetSemantics { },
                verticalArrangement = Arrangement.spacedBy(CARD_LINE_GAP),
            ) {
                Text(
                    text = card.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Always one line, empty for a movie nobody started, so every card is as tall as the skeleton.
                Text(
                    text = card.subtitle.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    minLines = 1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onLongClick, modifier = Modifier.size(32.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_more_horiz),
                    contentDescription = Strings.moreActions,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A round outline with a play triangle, over the picture. It says "tap to play"; the whole card does the playing. */
@Composable
private fun PlayDisc(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(PLAY_DISC)
            .clip(CircleShape)
            .background(VibeyFinTokens.Colors.scrim.copy(alpha = 0.45f))
            .border(1.dp, VibeyFinTokens.Colors.ink.copy(alpha = 0.6f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_play),
            contentDescription = null,
            tint = VibeyFinTokens.Colors.ink,
            modifier = Modifier.size(24.dp),
        )
    }
}

/** Aqua is playback progress, here and in the player. The unfilled part is the `progress-track` token. */
@Composable
private fun ProgressBar(progress: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(PROGRESS_HEIGHT).background(VibeyFinTokens.Colors.progressTrack)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(VibeyFinTokens.Colors.accent))
    }
}

/**
 * Stands in for the row while it loads: the same cards, grey, so nothing moves when they arrive. Not
 * animated; looping motion is an opt-in setting (`brand.md`).
 */
@Composable
fun ContinueSkeletonCards(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = Strings.loadingContinueWatching },
        horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3),
    ) {
        repeat(3) {
            Column(Modifier.width(VibeyFinTokens.Size.thumbWidth)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(CARD_ASPECT)
                        .clip(RoundedCornerShape(VibeyFinTokens.Radius.artwork))
                        .background(VibeyFinTokens.Colors.surface2),
                )
                Column(Modifier.padding(top = VibeyFinTokens.Space.s2), verticalArrangement = Arrangement.spacedBy(CARD_LINE_GAP)) {
                    SkeletonLine(140.dp, MaterialTheme.typography.titleSmall)
                    SkeletonLine(72.dp, MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
