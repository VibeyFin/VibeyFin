package app.vibeyfin.app.item

import kotlin.test.Test
import kotlin.test.assertEquals

class ScrollDarkeningTest {
    @Test
    fun clearAtTheTop() = assertEquals(0f, scrollDarkening(0f, 400f))

    @Test
    fun halfwayToFullyDark() = assertEquals(0.47f, scrollDarkening(110f, 400f), 0.0001f)

    @Test
    fun stopsAtNearlyThePageColour() = assertEquals(0.94f, scrollDarkening(10_000f, 400f))

    @Test
    fun noHeightStaysClear() = assertEquals(0f, scrollDarkening(50f, 0f))
}
