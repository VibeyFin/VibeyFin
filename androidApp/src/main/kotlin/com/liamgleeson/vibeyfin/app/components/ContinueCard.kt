package com.liamgleeson.vibeyfin.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.liamgleeson.vibeyfin.app.theme.VibeyFinTokens
import com.liamgleeson.vibeyfin.ui.common.Strings
import com.liamgleeson.vibeyfin.ui.home.ContinueCard

private const val CARD_ASPECT = 16f / 9f
private val PROGRESS_HEIGHT = 3.dp

/**
 * A 16:9 card in the Continue watching row: the picture, the aqua progress bar along its bottom edge
 * if something is in progress, and the title with the episode under it. Tapping plays; there is no
 * detail page in between. A long press opens the card's actions.
 */
@Composable
fun ContinueCard(card: ContinueCard, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .width(VibeyFinTokens.Size.thumbWidth)
            .clip(RoundedCornerShape(VibeyFinTokens.Radius.artwork))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics(mergeDescendants = true) { contentDescription = card.accessibilityLabel },
        verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2),
    ) {
        Box(Modifier.clip(RoundedCornerShape(VibeyFinTokens.Radius.artwork))) {
            Artwork(url = card.imageUrl, title = card.title, aspectRatio = CARD_ASPECT, modifier = Modifier.fillMaxWidth())
            card.progress?.let { ProgressBar(it, Modifier.align(Alignment.BottomStart)) }
        }
        Text(
            text = card.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // Always one line, empty for a movie, so every card is the same height as the skeleton.
        Text(
            text = card.subtitle.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            minLines = 1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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
            Column(Modifier.width(VibeyFinTokens.Size.thumbWidth), verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(CARD_ASPECT)
                        .clip(RoundedCornerShape(VibeyFinTokens.Radius.artwork))
                        .background(VibeyFinTokens.Colors.surface2),
                )
                SkeletonLine(140.dp, MaterialTheme.typography.titleSmall)
                SkeletonLine(72.dp, MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** A grey bar as tall as one line of [style], so the skeleton is exactly as tall as the real card. */
@Composable
private fun SkeletonLine(width: androidx.compose.ui.unit.Dp, style: androidx.compose.ui.text.TextStyle) {
    Box(Modifier.width(width)) {
        Text(text = "", style = style, minLines = 1)
        Box(
            Modifier
                .matchParentSize()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(VibeyFinTokens.Radius.m3Xs))
                .background(VibeyFinTokens.Colors.surface2),
        )
    }
}
