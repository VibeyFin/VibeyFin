package app.vibeyfin.ui.common

import kotlin.math.floor

/** How many placeholder tiles a row of skeletons draws. Lengths are in the screen's own units (dp, points). */
object PlaceholderCount {
    /** The real [known] count when the screen has it; otherwise the tiles that fill [visibleWidth] plus one peeking past the edge. */
    fun forRow(visibleWidth: Float, tileWidth: Float, spacing: Float, known: Int?): Int {
        if (known != null) return known.coerceAtLeast(0)
        val fullTiles: Int = floor((visibleWidth + spacing) / (tileWidth + spacing)).toInt()

        return fullTiles + 1
    }
}
