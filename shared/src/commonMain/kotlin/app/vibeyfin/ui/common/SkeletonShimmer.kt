package app.vibeyfin.ui.common

/**
 * Where the shimmer's band is across the screen at a moment, the same for every placeholder on it, so one light sweeps them all
 * together. Positions are in the screen's own units (dp, points), from its left edge.
 */
object SkeletonShimmer {
    const val PERIOD_MILLIS: Long = 2_000L

    private const val SWEEP_MILLIS: Long = 1_500L
    private const val BAND_FRACTION: Float = 0.2f

    /** Half the band's width: it fades in and out over this much each side of [bandCenter]. */
    fun bandHalfWidth(sweepWidth: Float): Float = sweepWidth * BAND_FRACTION

    /** The band's centre [elapsedMillis] into the animation: across [sweepWidth] and off both edges, then a rest off the right one. */
    fun bandCenter(elapsedMillis: Long, sweepWidth: Float): Float {
        val half: Float = bandHalfWidth(sweepWidth)
        val inCycle: Long = elapsedMillis.mod(PERIOD_MILLIS)
        val travelled: Float = (inCycle.coerceAtMost(SWEEP_MILLIS).toFloat() / SWEEP_MILLIS)

        return -half + travelled * (sweepWidth + 2 * half)
    }
}
