package app.vibeyfin.app.player

import android.view.Surface
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerTurnTest {
    private val widescreen = 16f / 9f

    @Test
    fun `opening starts upright on the held phone, a quarter turn back from the new landscape window`() {
        assertEquals(-90f, turnFrom(Surface.ROTATION_0, Surface.ROTATION_90, 2000f, 1000f, widescreen).degrees)
        assertEquals(90f, turnFrom(Surface.ROTATION_0, Surface.ROTATION_270, 2000f, 1000f, widescreen).degrees)
    }

    @Test
    fun `closing starts sideways on the held phone, a quarter turn back from the new portrait window`() {
        assertEquals(90f, turnFrom(Surface.ROTATION_90, Surface.ROTATION_0, 1000f, 2000f, widescreen).degrees)
        assertEquals(-90f, turnFrom(Surface.ROTATION_270, Surface.ROTATION_0, 1000f, 2000f, widescreen).degrees)
    }

    @Test
    fun `opening starts with the picture as wide as the portrait screen`() {
        // In a 2000x1000 window a 16:9 picture is 1778 wide; upright on the 1000 wide phone it was 1000 wide
        assertEquals(0.5625f, turnFrom(Surface.ROTATION_0, Surface.ROTATION_90, 2000f, 1000f, widescreen).scale, 0.001f)
    }

    @Test
    fun `closing starts with the picture as tall as the landscape screen`() {
        assertEquals(1.778f, turnFrom(Surface.ROTATION_90, Surface.ROTATION_0, 1000f, 2000f, widescreen).scale, 0.001f)
    }

    @Test
    fun `a picture wider than the landscape screen starts from the full width`() {
        // 2.39:1 fills the 2000 wide window in landscape, and the 1000 wide screen when upright
        assertEquals(0.5f, turnFrom(Surface.ROTATION_0, Surface.ROTATION_90, 2000f, 1000f, 2.39f).scale, 0.001f)
    }

    @Test
    fun `a vertical video fills the portrait screen and only the height in landscape`() {
        // 9:16 is 1000 wide upright, and 562 wide in landscape where the 1000 high window limits it
        assertEquals(1.778f, turnFrom(Surface.ROTATION_0, Surface.ROTATION_90, 2000f, 1000f, 9f / 16f).scale, 0.001f)
    }

    private val coverScreen = PlayerScreenFacts(landscape = false, smallestWidthDp = 475, sdk = 37, multiWindow = false)

    @Test
    fun `a phone held upright turns on opening`() {
        assertTrue(opensWithTurn(coverScreen))
    }

    @Test
    fun `a screen that is already landscape does not turn on opening`() {
        assertFalse(opensWithTurn(coverScreen.copy(landscape = true)))
    }

    @Test
    fun `a large screen on Android 16 and up ignores the request and does not turn`() {
        assertFalse(opensWithTurn(coverScreen.copy(smallestWidthDp = 704, sdk = 36)))
        assertTrue(opensWithTurn(coverScreen.copy(smallestWidthDp = 704, sdk = 35)))
    }

    @Test
    fun `a window sharing the screen does not turn`() {
        assertFalse(opensWithTurn(coverScreen.copy(multiWindow = true)))
    }

    private val turnedPlayer = coverScreen.copy(landscape = true)

    @Test
    fun `closing turns back when the phone is upright or its position is unknown`() {
        assertTrue(closesWithTurn(turnedPlayer, rotationLocked = false, openedUpright = true, heldSideways = false))
        assertTrue(closesWithTurn(turnedPlayer, rotationLocked = false, openedUpright = true, heldSideways = null))
    }

    @Test
    fun `closing does not turn when the phone is held sideways with auto-rotate on`() {
        assertFalse(closesWithTurn(turnedPlayer, rotationLocked = false, openedUpright = true, heldSideways = true))
    }

    @Test
    fun `with the rotation lock on the screen goes back to how it was opened, however it is held`() {
        assertTrue(closesWithTurn(turnedPlayer, rotationLocked = true, openedUpright = true, heldSideways = true))
        assertFalse(closesWithTurn(turnedPlayer, rotationLocked = true, openedUpright = false, heldSideways = false))
    }

    @Test
    fun `closing a window that never became landscape does not turn`() {
        assertFalse(closesWithTurn(coverScreen, rotationLocked = false, openedUpright = true, heldSideways = false))
    }

    @Test
    fun `closing on a screen that ignores the request does not turn`() {
        val inner = turnedPlayer.copy(smallestWidthDp = 704)

        assertFalse(closesWithTurn(inner, rotationLocked = false, openedUpright = false, heldSideways = false))
    }
}
