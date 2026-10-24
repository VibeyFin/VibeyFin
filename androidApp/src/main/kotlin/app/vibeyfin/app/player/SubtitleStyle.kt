package app.vibeyfin.app.player

import android.content.Context
import android.view.accessibility.CaptioningManager
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView

// Caption colours over video, not brand tokens.
internal const val SUBTITLE_WHITE = 0xFFFFFFFF.toInt()
internal const val SUBTITLE_YELLOW = 0xFFFFEB3B.toInt()
internal const val SUBTITLE_LIGHT_BLUE = 0xFF8FD3FF.toInt()
internal const val SUBTITLE_BOX = 0xB3000000.toInt()
internal const val SUBTITLE_SHADOW = 0xFF000000.toInt()
internal const val SUBTITLE_TRANSPARENT = 0x00000000

/** Subtitle text size as a fraction of the player height, for sizes S, M, L, XL. */
internal val SUBTITLE_SIZE_FRACTIONS = floatArrayOf(0.04f, 0.0533f, 0.07f, 0.09f)
private val SUBTITLE_COLORS = intArrayOf(SUBTITLE_WHITE, SUBTITLE_YELLOW, SUBTITLE_LIGHT_BLUE)

/** How [CaptionStyleCompat] edges are drawn; same values as its EDGE_TYPE_* constants. */
internal const val SUBTITLE_EDGE_NONE = 0
internal const val SUBTITLE_EDGE_DROP_SHADOW = 2

/**
 * What the subtitle view should show. [followSystem] means the OS caption settings win and the other
 * values are unused.
 */
internal data class SubtitleViewStyle(
    val followSystem: Boolean,
    val sizeFraction: Float,
    val foreground: Int,
    val background: Int,
    val edgeType: Int,
    val edgeColor: Int,
)

internal fun subtitleStyle(followSystem: Boolean, size: Int, color: Int, background: Int): SubtitleViewStyle =
    SubtitleViewStyle(
        followSystem = followSystem,
        sizeFraction = SUBTITLE_SIZE_FRACTIONS[size.coerceIn(0, SUBTITLE_SIZE_FRACTIONS.lastIndex)],
        foreground = SUBTITLE_COLORS[color.coerceIn(0, SUBTITLE_COLORS.lastIndex)],
        background = if (background == 2) SUBTITLE_BOX else SUBTITLE_TRANSPARENT,
        edgeType = if (background == 1) SUBTITLE_EDGE_DROP_SHADOW else SUBTITLE_EDGE_NONE,
        edgeColor = SUBTITLE_SHADOW,
    )

internal fun SubtitleView.apply(style: SubtitleViewStyle) {
    if (style.followSystem) {
        val captioning = context.getSystemService(Context.CAPTIONING_SERVICE) as CaptioningManager
        setStyle(CaptionStyleCompat.createFromCaptionStyle(captioning.userStyle))
        setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * captioning.fontScale)
    } else {
        setStyle(
            CaptionStyleCompat(
                style.foreground,
                style.background,
                SUBTITLE_TRANSPARENT,
                style.edgeType,
                style.edgeColor,
                null,
            ),
        )
        setFractionalTextSize(style.sizeFraction)
    }
}
