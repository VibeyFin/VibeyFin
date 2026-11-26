package app.vibeyfin.app.components

import app.vibeyfin.ui.common.PlaceholderCount

/** A rectangle in a text's own pixels: where a line of it sits, or the gray bar drawn in its place. */
data class BarBounds(val left: Float, val top: Float, val right: Float, val bottom: Float)

object SkeletonBars {
    private const val MIN_WIDTH_FRACTION: Float = 0.55f
    private const val HASH_MULTIPLIER: Int = 31
    private const val HASH_BUCKETS: Int = 1000

    /** One bar per line that has any width, [insetFraction] of the line's height shorter at the top and at the bottom, and a varied share of the line's width so a stack of bars does not look cut from one length. */
    fun forLines(lines: List<BarBounds>, insetFraction: Float): List<BarBounds> = lines
        .filter { it.right > it.left }
        .map { line ->
            val inset: Float = (line.bottom - line.top) * insetFraction
            val width: Float = (line.right - line.left) * widthFraction(line)
            val left: Float = if (line.left <= 0f) line.left else (line.left + line.right - width) / 2f

            BarBounds(left, line.top + inset, left + width, line.bottom - inset)
        }

    /** The same line always gets the same share, between [MIN_WIDTH_FRACTION] and 1, so a skeleton does not change shape while it shimmers. */
    fun widthFraction(line: BarBounds): Float {
        val seed: Int = (line.top.toInt() * HASH_MULTIPLIER + line.left.toInt()) * HASH_MULTIPLIER + line.right.toInt()
        val bucket: Int = ((seed % HASH_BUCKETS) + HASH_BUCKETS) % HASH_BUCKETS

        return MIN_WIDTH_FRACTION + (1f - MIN_WIDTH_FRACTION) * bucket / HASH_BUCKETS
    }

    /** The tiles a grid draws: whole rows that fill [visibleHeight] plus one peeking past the edge, at [columns] across. */
    fun gridCount(columns: Int, visibleHeight: Float, rowHeight: Float, rowSpacing: Float): Int = columns * PlaceholderCount.forRow(visibleHeight, rowHeight, rowSpacing, known = null)
}
