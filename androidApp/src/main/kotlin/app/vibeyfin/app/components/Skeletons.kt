package app.vibeyfin.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.ui.common.Strings

/*
 * Grey stand-ins for content that is still loading: the same shapes as the real thing, so nothing moves
 * when it arrives. Not animated; looping motion is an opt-in setting (`brand.md`). A spinner is only for
 * an action in progress (a button, the player, "loading more").
 */

/** A grey block with the artwork corner radius. */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(VibeyFinTokens.Radius.artwork)) {
    Box(modifier.clip(shape).background(VibeyFinTokens.Colors.surface2))
}

/** A grey bar as tall as one line of [style], so the skeleton is exactly as tall as the real text. */
@Composable
fun SkeletonLine(width: Dp, style: TextStyle) {
    Box(Modifier.width(width)) {
        Text(text = "", style = style, minLines = 1)
        Box(
            Modifier
                .matchParentSize()
                .padding(vertical = VibeyFinTokens.Space.s1)
                .clip(RoundedCornerShape(VibeyFinTokens.Radius.m3Xs))
                .background(VibeyFinTokens.Colors.surface2),
        )
    }
}

/** Poster tiles for a lazy grid: [count] grey posters with a title line under each. */
fun LazyGridScope.skeletonTiles(count: Int = 6, aspectRatio: Float = 2f / 3f) {
    items(count, key = { "skeleton-$it" }) {
        Column(
            Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = Strings.loadingPage },
            verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2),
        ) {
            SkeletonBlock(Modifier.fillMaxWidth().aspectRatio(aspectRatio))
            SkeletonLine(96.dp, MaterialTheme.typography.titleSmall)
        }
    }
}

/** The item page while it loads: the picture, the title, the facts line, the action and the overview. */
@Composable
fun ItemSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clearAndSetSemantics { contentDescription = Strings.loadingPage },
        verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3),
    ) {
        SkeletonBlock(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        SkeletonLine(220.dp, MaterialTheme.typography.titleLarge)
        SkeletonLine(160.dp, MaterialTheme.typography.bodyMedium)
        SkeletonBlock(Modifier.fillMaxWidth().height(VibeyFinTokens.Size.buttonLarge), RoundedCornerShape(VibeyFinTokens.Radius.m3Xs))
        repeat(3) { SkeletonLine(280.dp, MaterialTheme.typography.bodyLarge) }
    }
}

/** Round profile avatars with a name line, for "Who's watching?" while the profiles load. */
fun LazyGridScope.skeletonProfiles(count: Int, avatarSize: Dp) {
    items(count, key = { "skeleton-profile-$it" }) {
        Column(
            Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = Strings.loadingPage },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2),
        ) {
            SkeletonBlock(Modifier.size(avatarSize), CircleShape)
            SkeletonLine(72.dp, MaterialTheme.typography.bodyMedium)
        }
    }
}

/** A row of grey tiles at [tileWidth], for a row of posters or library tiles while it loads. */
@Composable
fun PosterSkeletonRow(
    modifier: Modifier = Modifier,
    count: Int = 4,
    tileWidth: Dp = VibeyFinTokens.Size.posterWidth,
    aspectRatio: Float = 2f / 3f,
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = Strings.loadingPage },
        horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3),
    ) {
        repeat(count) {
            Column(Modifier.width(tileWidth), verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2)) {
                SkeletonBlock(Modifier.fillMaxWidth().aspectRatio(aspectRatio))
                SkeletonLine(72.dp, MaterialTheme.typography.titleSmall)
            }
        }
    }
}
