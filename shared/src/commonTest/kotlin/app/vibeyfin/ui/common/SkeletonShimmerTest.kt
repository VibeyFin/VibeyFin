package app.vibeyfin.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals

class SkeletonShimmerTest {
    private val screen = 1000f

    @Test
    fun `the band is a fifth of the screen each side of its centre`() {
        assertEquals(200f, SkeletonShimmer.bandHalfWidth(screen))
    }

    @Test
    fun `the band starts just off the left edge and is halfway across halfway through the sweep`() {
        assertEquals(-200f, SkeletonShimmer.bandCenter(0, screen))
        assertEquals(500f, SkeletonShimmer.bandCenter(750, screen))
    }

    @Test
    fun `after the sweep the band rests off the right edge until the next one`() {
        assertEquals(1200f, SkeletonShimmer.bandCenter(1500, screen))
        assertEquals(1200f, SkeletonShimmer.bandCenter(1999, screen))
    }

    @Test
    fun `the sweep repeats every two seconds`() {
        assertEquals(-200f, SkeletonShimmer.bandCenter(2000, screen))
        assertEquals(500f, SkeletonShimmer.bandCenter(60_750, screen))
    }
}
