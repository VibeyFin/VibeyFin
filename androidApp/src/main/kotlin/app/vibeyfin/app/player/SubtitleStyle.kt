package app.vibeyfin.app.player

import android.content.Context
import android.view.accessibility.CaptioningManager
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView

// Caption colors over video, not brand tokens
internal const val SUBTITLE_WHITE = 0xFFFFFFFF.toInt()
internal const val SUBTITLE_YELLOW = 0xFFFFEB3B.toInt()
internal const val SUBTITLE_LIGHT_BLUE = 0xFF8FD3FF.toInt()
internal const val SUBTITLE_BOX = 0xB3000000.toInt()
internal const val SUBTITLE_SHADOW = 0xFF000000.toInt()
internal const val SUBTITLE_TRANSPARENT = 0x00000000

/** Subtitle text size as a fraction of the player height, for sizes S, M, L, XL. */
internal val SUBTITLE_SIZE_FRACTIONS = floatArrayOf(0.04f, 0.0533f, 0.07f, 0.09f)
private val SUBTITLE_COLORS = intArrayOf(SUBTITLE_WHITE, SUBTITLE_YELLOW, SUBTITLE_LIGHT_BLUE)

// The values of the Subtitle background setting (see PlaybackUiState.subtitleBackground)
private const val BACKGROUND_SHADOW = 1
private const val BACKGROUND_BOX = 2

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

internal fun subtitleStyle(followSystem: Boolean, size: Int, color: Int, background: Int): SubtitleViewStyle = SubtitleViewStyle(
    followSystem = followSystem,
    sizeFraction = SUBTITLE_SIZE_FRACTIONS[size.coerceIn(0, SUBTITLE_SIZE_FRACTIONS.lastIndex)],
    foreground = SUBTITLE_COLORS[color.coerceIn(0, SUBTITLE_COLORS.lastIndex)],
    background = if (background == BACKGROUND_BOX) SUBTITLE_BOX else SUBTITLE_TRANSPARENT,
    edgeType = if (background == BACKGROUND_SHADOW) CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW else CaptionStyleCompat.EDGE_TYPE_NONE,
    edgeColor = SUBTITLE_SHADOW,
)

internal fun SubtitleView.apply(style: SubtitleViewStyle) {
    if (style.followSystem) {
        val captioning = context.getSystemService(Context.CAPTIONING_SERVICE) as CaptioningManager

        setApplyEmbeddedStyles(true)
        setStyle(CaptionStyleCompat.createFromCaptionStyle(captioning.userStyle))
        setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * captioning.fontScale)
    } else {
        // The user chose explicit colors, so a subtitle's own styling must not override them
        setApplyEmbeddedStyles(false)
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
