package app.vibeyfin.app.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class LeftoverFlingAbsorberTest {
    private val fling = Velocity(0f, 4000f)

    @Test
    fun aFlingLeftOverFromTheListNeverReachesASheetThatDidNotMove() = runBlocking {
        val absorber = LeftoverFlingAbsorber()

        absorber.onPostScroll(Offset(0f, 300f), Offset.Zero, NestedScrollSource.UserInput)
        assertEquals(fling, absorber.onPostFling(Velocity.Zero, fling))
    }

    @Test
    fun aFlingAfterPullingTheSheetGoesOnToDismissIt() = runBlocking {
        val absorber = LeftoverFlingAbsorber()

        absorber.onPostScroll(Offset.Zero, Offset(0f, 300f), NestedScrollSource.UserInput)
        assertEquals(Velocity.Zero, absorber.onPostFling(Velocity.Zero, fling))
    }

    @Test
    fun eachGestureStartsAgain() = runBlocking {
        val absorber = LeftoverFlingAbsorber()

        absorber.onPostScroll(Offset.Zero, Offset(0f, 300f), NestedScrollSource.UserInput)
        absorber.onPostFling(Velocity.Zero, fling)
        assertEquals(fling, absorber.onPostFling(Velocity.Zero, fling))
    }

    @Test
    fun swipingUpPastTheEndNeverReachesTheSheet() = runBlocking {
        val absorber = LeftoverFlingAbsorber()
        val up = Offset(0f, -300f)
        val flingUp = Velocity(0f, -4000f)

        assertEquals(up, absorber.onPostScroll(Offset.Zero, up, NestedScrollSource.UserInput))
        assertEquals(flingUp, absorber.onPostFling(Velocity.Zero, flingUp))
    }

    @Test
    fun theListsOwnFlingDoesNotCountAsMovingTheSheet() = runBlocking {
        val absorber = LeftoverFlingAbsorber()

        absorber.onPostScroll(Offset.Zero, Offset(0f, 300f), NestedScrollSource.SideEffect)
        assertEquals(fling, absorber.onPostFling(Velocity.Zero, fling))
    }
}
