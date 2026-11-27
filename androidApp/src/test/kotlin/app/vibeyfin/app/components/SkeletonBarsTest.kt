package app.vibeyfin.app.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SkeletonBarsTest {
    @Test
    fun aBarIsInsetFromTopAndBottomOfItsLine() {
        val line = BarBounds(left = 0f, top = 0f, right = 100f, bottom = 20f)

        val bar: BarBounds = SkeletonBars.forLines(listOf(line), insetFraction = 0.2f).single()

        assertEquals(4f, bar.top)
        assertEquals(16f, bar.bottom)
    }

    @Test
    fun aBarIsNeverLongerThanItsLineNorShorterThanTheMinimumShare() {
        val lines: List<BarBounds> = (0 until 40).map { BarBounds(0f, it * 20f, 100f, it * 20f + 20f) }

        SkeletonBars.forLines(lines, insetFraction = 0f).forEach { bar ->
            val width: Float = bar.right - bar.left

            assertTrue(width <= 100f && width >= 55f)
        }
    }

    @Test
    fun linesOfTheSameLengthGetBarsOfDifferentLengths() {
        val lines: List<BarBounds> = (0 until 6).map { BarBounds(0f, it * 20f, 100f, it * 20f + 20f) }

        val widths: Set<Float> = SkeletonBars.forLines(lines, insetFraction = 0f).map { it.right - it.left }.toSet()

        assertTrue(widths.size > 3)
    }

    @Test
    fun theSameLineAlwaysGetsTheSameBar() {
        val line = BarBounds(0f, 40f, 100f, 60f)

        assertEquals(SkeletonBars.forLines(listOf(line), 0.2f), SkeletonBars.forLines(listOf(line), 0.2f))
    }

    @Test
    fun aCenteredLineStaysCentered() {
        val line = BarBounds(left = 20f, top = 0f, right = 120f, bottom = 20f)

        val bar: BarBounds = SkeletonBars.forLines(listOf(line), insetFraction = 0f).single()

        assertEquals(70f, (bar.left + bar.right) / 2f, absoluteTolerance = 0.01f)
    }

    @Test
    fun anEmptyLineDrawsNoBar() {
        val bars: List<BarBounds> = SkeletonBars.forLines(listOf(BarBounds(0f, 0f, 0f, 20f)), insetFraction = 0.2f)

        assertTrue(bars.isEmpty())
    }

    @Test
    fun aGridFillsTheScreenHeightPlusOnePeekingRow() {
        val tiles: Int = SkeletonBars.gridCount(columns = 3, visibleHeight = 800f, rowHeight = 200f, rowSpacing = 0f)

        assertEquals(15, tiles)
    }
}
