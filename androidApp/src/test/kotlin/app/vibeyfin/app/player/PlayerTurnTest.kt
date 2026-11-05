package app.vibeyfin.app.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerTurnTest {
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
    fun `closing turns back when the phone is upright`() {
        assertTrue(closesWithTurn(turnedPlayer, rotationLocked = false, openedUpright = true, heldSideways = false))
    }

    @Test
    fun `a phone lying flat keeps its rotation, so closing does not wait for a turn`() {
        assertFalse(closesWithTurn(turnedPlayer, rotationLocked = false, openedUpright = true, heldSideways = null))
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
