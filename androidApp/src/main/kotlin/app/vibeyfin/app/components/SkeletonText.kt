package app.vibeyfin.app.components

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import app.vibeyfin.app.theme.VibeyFinTokens

private const val BAR_INSET_FRACTION = 0.2f

/**
 * The app's text. Under [Skeleton] its words are not drawn: each line is a gray bar as long as the words and as tall as the line,
 * so a skeleton is as tall as the real text.
 */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    fontWeight: FontWeight? = null,
    style: TextStyle = LocalTextStyle.current,
    autoSize: TextAutoSize? = null,
) {
    if (!LocalSkeleton.current) {
        androidx.compose.material3.Text(text, modifier, color, fontWeight = fontWeight, textAlign = textAlign, overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines, onTextLayout = onTextLayout, style = style, autoSize = autoSize)

        return
    }

    var laidOut: TextLayoutResult? by remember { mutableStateOf(null) }
    val band: ShimmerBand = rememberShimmerBand()
    val barColor: Color = VibeyFinTokens.Colors.surface3
    val radius: Float = with(androidx.compose.ui.platform.LocalDensity.current) { VibeyFinTokens.Radius.m3Xs.toPx() }

    androidx.compose.material3.Text(
        text = text,
        modifier = modifier
            .then(band.track())
            .drawBehind {
                val layout: TextLayoutResult = laidOut ?: return@drawBehind
                val lines: List<BarBounds> = (0 until layout.lineCount).map { BarBounds(layout.getLineLeft(it), layout.getLineTop(it), layout.getLineRight(it), layout.getLineBottom(it)) }

                SkeletonBars.forLines(lines, BAR_INSET_FRACTION).forEach { bar ->
                    val topLeft = Offset(bar.left, bar.top)
                    val size = Size(bar.right - bar.left, bar.bottom - bar.top)

                    drawRoundRect(barColor, topLeft, size, CornerRadius(radius))
                    band.brush()?.let { drawRoundRect(it, topLeft, size, CornerRadius(radius)) }
                }
            },
        color = Color.Transparent,
        fontWeight = fontWeight,
        textAlign = textAlign,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = {
            laidOut = it
            onTextLayout?.invoke(it)
        },
        style = style,
        autoSize = autoSize,
    )
}
