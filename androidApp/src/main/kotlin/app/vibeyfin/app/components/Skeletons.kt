package app.vibeyfin.app.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.app.theme.animationsEnabled
import app.vibeyfin.ui.common.SkeletonShimmer
import app.vibeyfin.ui.common.Strings

/*
 * Gray stand-ins for content that is still loading: the same shapes as the real thing, so nothing moves
 * when it arrives. They shimmer while animations are on (`brand.md`).
 * A spinner is only for an action in progress (a button, the player, "loading more").
 */

private val LocalSkeletonShimmerElapsedMillis = staticCompositionLocalOf<State<Float>?> { null }

/** One clock for every skeleton under [content], so a single light sweeps the whole screen; none while animations are off. */
@Composable
fun ProvideSkeletonShimmer(content: @Composable () -> Unit) {
    if (!animationsEnabled()) {
        content()
        return
    }

    val elapsed: State<Float> = rememberInfiniteTransition("skeleton-shimmer").animateFloat(
        initialValue = 0f,
        targetValue = SkeletonShimmer.PERIOD_MILLIS.toFloat(),
        animationSpec = infiniteRepeatable(tween(SkeletonShimmer.PERIOD_MILLIS.toInt(), easing = LinearEasing), RepeatMode.Restart),
        label = "skeleton-shimmer-elapsed",
    )

    CompositionLocalProvider(LocalSkeletonShimmerElapsedMillis provides elapsed, content = content)
}

/** A gray block with the artwork corner radius. */
@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(VibeyFinTokens.Radius.artwork),
    color: Color = VibeyFinTokens.Colors.surface2,
) {
    Box(modifier.clip(shape).background(color).then(shimmer()))
}

@Composable
private fun shimmer(): Modifier {
    val elapsed: State<Float> = LocalSkeletonShimmerElapsedMillis.current ?: return Modifier
    val sweepWidth: Float = LocalWindowInfo.current.containerSize.width.toFloat()
    val originX: FloatArray = remember { FloatArray(1) }
    val highlight: Color = VibeyFinTokens.Colors.line.copy(alpha = SHIMMER_ALPHA)

    // The band is placed in screen coordinates and read at draw time, so the blocks line up and nothing recomposes per frame
    return Modifier
        .onGloballyPositioned { originX[0] = it.positionInRoot().x }
        .drawWithContent {
            val center: Float = SkeletonShimmer.bandCenter(elapsed.value.toLong(), sweepWidth) - originX[0]
            val half: Float = SkeletonShimmer.bandHalfWidth(sweepWidth)

            drawContent()
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, highlight, Color.Transparent), startX = center - half, endX = center + half))
        }
}

private const val SHIMMER_ALPHA = 0.5f

/**
 * How many placeholders a row or grid draws: [phone] on a compact width, twice as many from 600 dp (a tablet, an unfolded
 * foldable), where the same count would leave the row looking nearly empty. A row that scrolls never shows the extras.
 */
@Composable
fun skeletonCount(phone: Int): Int = if (LocalConfiguration.current.screenWidthDp >= WIDE_FROM_DP) phone * 2 else phone

private const val WIDE_FROM_DP = 600

/** A gray bar as tall as one line of [style], so the skeleton is exactly as tall as the real text. */
@Composable
fun SkeletonLine(width: Dp, style: TextStyle) {
    Box(Modifier.width(width)) {
        Text(text = "", style = style, minLines = 1)
        SkeletonBlock(
            Modifier.matchParentSize().padding(vertical = VibeyFinTokens.Space.s1),
            RoundedCornerShape(VibeyFinTokens.Radius.m3Xs),
        )
    }
}

/** Poster tiles for a lazy grid: [count] gray posters with a title line under each. */
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

/**
 * The detail page while it loads, in the shapes the page has: on a phone the tall picture with the logo, the facts, Play and the four
 * actions at its foot, then the cast; on a tablet the picture behind a column at the left. So nothing moves when the page arrives.
 */
@Composable
fun ItemSkeleton(wide: Boolean, screenWidth: Dp, screenHeight: Dp, margin: Dp, modifier: Modifier = Modifier) {
    val pill = RoundedCornerShape(50)
    val bar = RoundedCornerShape(VibeyFinTokens.Radius.m3Xs)
    val line = VibeyFinTokens.Colors.surface3

    Box(modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = Strings.loadingPage }) {
        if (wide) {
            SkeletonBlock(Modifier.fillMaxWidth().height(screenHeight * 0.7f), RoundedCornerShape(0.dp))
        } else {
            SkeletonBlock(Modifier.fillMaxWidth().height(screenWidth * 1.5f), RoundedCornerShape(0.dp))
        }

        Column(
            Modifier.fillMaxWidth().then(if (wide) Modifier.fillMaxWidth(0.46f) else Modifier).padding(horizontal = margin)
                .padding(top = if (wide) screenHeight * 0.3f else screenWidth * 1.5f - 360.dp),
            horizontalAlignment = if (wide) Alignment.Start else Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3),
        ) {
            SkeletonBlock(Modifier.fillMaxWidth(0.6f).height(56.dp), bar, line)
            SkeletonBlock(Modifier.width(150.dp).height(16.dp), bar, line)
            SkeletonBlock(Modifier.fillMaxWidth().height(40.dp), bar, line)
            SkeletonBlock(Modifier.fillMaxWidth().height(VibeyFinTokens.Size.buttonLarge), pill, line)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                repeat(4) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s1)) {
                        SkeletonBlock(Modifier.size(30.dp), CircleShape, line)
                        SkeletonBlock(Modifier.width(56.dp).height(14.dp), bar, line)
                    }
                }
            }
        }
    }
}

fun LazyGridScope.skeletonProfiles(count: Int, avatarSize: Dp) {
    items(count, key = { "skeleton-profile-$it" }) {
        Column(
            Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = Strings.loadingPage },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2),
        ) {
            SkeletonBlock(Modifier.size(avatarSize), RoundedCornerShape(VibeyFinTokens.Radius.m3Md))
            SkeletonLine(72.dp, MaterialTheme.typography.bodyMedium)
        }
    }
}

/** A row of gray tiles at [tileWidth], for a row of posters or library tiles while it loads. */
@Composable
fun PosterSkeletonRow(
    modifier: Modifier = Modifier,
    count: Int = 4,
    tileWidth: Dp = VibeyFinTokens.Size.posterWidth,
    aspectRatio: Float = 2f / 3f,
    captionLines: Int = 2,
) {
    // Wider than a phone, so the row measures its tiles unbounded and the box cuts it off at the edge: no tile is squeezed
    Box(modifier.clipToBounds().clearAndSetSemantics { contentDescription = Strings.loadingPage }) {
        Row(
            modifier = Modifier.wrapContentWidth(Alignment.Start, unbounded = true),
            horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3),
        ) {
            repeat(skeletonCount(count)) {
                Column(Modifier.width(tileWidth), verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2)) {
                    SkeletonBlock(Modifier.fillMaxWidth().aspectRatio(aspectRatio))
                    repeat(captionLines) { line ->
                        SkeletonLine(if (line == 0) 72.dp else 48.dp, MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
    }
}
