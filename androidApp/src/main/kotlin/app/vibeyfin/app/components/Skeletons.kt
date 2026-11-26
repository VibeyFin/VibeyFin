package app.vibeyfin.app.components

import android.content.res.Configuration
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import app.vibeyfin.app.navigation.LocalSeerrLongPress
import app.vibeyfin.app.navigation.LocalTileLongPress
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.app.theme.animationsEnabled
import app.vibeyfin.ui.common.PlaceholderCount
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

/** True under [Skeleton]: the real views below draw as gray shapes of themselves, with placeholder data and nothing to tap. */
val LocalSkeleton = staticCompositionLocalOf { false }

/** Draws [content], a real view fed placeholder data, as gray shapes. It reads as one "loading" to a screen reader and opens nothing. */
@Composable
fun Skeleton(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.clearAndSetSemantics { contentDescription = Strings.loadingPage }) {
        CompositionLocalProvider(
            LocalSkeleton provides true,
            LocalTileLongPress provides null,
            LocalSeerrLongPress provides null,
            content = content,
        )
    }
}

/** A gray block with the artwork corner radius. */
@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(VibeyFinTokens.Radius.artwork),
    color: Color = VibeyFinTokens.Colors.surface2,
) {
    Box(modifier.skeletonFill(shape, color))
}

/** Under [Skeleton], draws this element's box as a gray shape in place of its content; layout is untouched. Put it first in the chain. */
@Composable
fun Modifier.skeleton(shape: Shape = RoundedCornerShape(VibeyFinTokens.Radius.artwork)): Modifier = if (LocalSkeleton.current) skeletonFill(shape, VibeyFinTokens.Colors.surface2) else this

@Composable
internal fun Modifier.skeletonFill(shape: Shape, color: Color): Modifier {
    val band: ShimmerBand = rememberShimmerBand()

    return this
        .clip(shape)
        .then(band.track())
        .drawWithContent {
            drawRect(color)
            band.brush()?.let { drawRect(it) }
        }
}

internal class ShimmerBand(private val elapsed: State<Float>?, private val sweepWidth: Float, private val highlight: Color) {
    private val originX: FloatArray = FloatArray(1)

    fun track(): Modifier = if (elapsed == null) Modifier else Modifier.onGloballyPositioned { originX[0] = it.positionInRoot().x }

    // The band is placed in screen coordinates and read at draw time, so the shapes line up and nothing recomposes per frame
    fun brush(): Brush? {
        val clock: State<Float> = elapsed ?: return null
        val center: Float = SkeletonShimmer.bandCenter(clock.value.toLong(), sweepWidth) - originX[0]
        val half: Float = SkeletonShimmer.bandHalfWidth(sweepWidth)

        return Brush.horizontalGradient(listOf(Color.Transparent, highlight, Color.Transparent), startX = center - half, endX = center + half)
    }
}

@Composable
internal fun rememberShimmerBand(): ShimmerBand {
    val elapsed: State<Float>? = LocalSkeletonShimmerElapsedMillis.current
    val sweepWidth: Float = LocalWindowInfo.current.containerSize.width.toFloat()
    val highlight: Color = VibeyFinTokens.Colors.line.copy(alpha = SHIMMER_ALPHA)

    return remember(elapsed, sweepWidth, highlight) { ShimmerBand(elapsed, sweepWidth, highlight) }
}

/**
 * A row of placeholder tiles, drawn by [tile] with the real tile's own view, that fills the screen's width and one tile more (or [known]
 * tiles, when the row's real count is known). It is the real row without the scroll.
 */
@Composable
fun SkeletonRow(
    tileWidth: Dp,
    margin: Dp,
    modifier: Modifier = Modifier,
    spacing: Dp = VibeyFinTokens.Space.s3,
    known: Int? = null,
    tile: @Composable (Int) -> Unit,
) {
    val screenWidth: Float = LocalConfiguration.current.screenWidthDp.toFloat()
    val count: Int = PlaceholderCount.forRow(screenWidth, tileWidth.value, spacing.value, known)

    Skeleton {
        LazyRow(
            modifier = modifier,
            userScrollEnabled = false,
            contentPadding = PaddingValues(horizontal = margin),
            horizontalArrangement = Arrangement.spacedBy(spacing),
        ) {
            items(count) { index -> tile(index) }
        }
    }
}

private const val SHIMMER_ALPHA = 0.5f

/**
 * How many placeholder tiles a grid of [minTileWidth] tiles draws: whole rows that fill the screen's height, plus one peeking row.
 * [tileHeightBeyondPicture] is what sits under each tile's picture (its caption).
 */
@Composable
fun placeholderGridCount(minTileWidth: Dp, aspectRatio: Float, margin: Dp, tileHeightBeyondPicture: Dp = POSTER_CAPTION_HEIGHT): Int {
    val configuration: Configuration = LocalConfiguration.current
    val spacing: Float = VibeyFinTokens.Space.s3.value
    val width: Float = configuration.screenWidthDp - margin.value * 2
    val columns: Int = maxOf(1, ((width + spacing) / (minTileWidth.value + spacing)).toInt())
    val tileWidth: Float = (width - (columns - 1) * spacing) / columns
    val rowHeight: Float = tileWidth / aspectRatio + tileHeightBeyondPicture.value

    return SkeletonBars.gridCount(columns, configuration.screenHeightDp.toFloat(), rowHeight, VibeyFinTokens.Space.s4.value)
}

// Two lines of the title style and the gap above them
private val POSTER_CAPTION_HEIGHT = 48.dp

/** [count] placeholder tiles in a lazy grid, each drawn by [tile]; [keyPrefix] tells two such runs in one grid apart. */
fun LazyGridScope.placeholderTiles(count: Int, keyPrefix: String, tile: @Composable (Int) -> Unit) {
    items(count, key = { "$keyPrefix-$it" }) { index -> Skeleton { tile(index) } }
}

/** [add] applied to this modifier, except under [Skeleton], where nothing may be tapped. */
@Composable
fun Modifier.unlessSkeleton(add: Modifier.() -> Modifier): Modifier = if (LocalSkeleton.current) this else add()
