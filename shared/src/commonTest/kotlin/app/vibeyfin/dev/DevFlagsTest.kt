package app.vibeyfin.dev

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DevFlagsTest {
    @Test
    fun aFlagStartsOff() {
        val flags = DevFlags(enabled = true)

        assertFalse(flags.state(DevFlagRegistry.showSkeletons).value)
    }

    @Test
    fun aDebugBuildKeepsWhatIsSet() {
        val flags = DevFlags(enabled = true)

        flags.set(DevFlagRegistry.showSkeletons, true)

        assertTrue(flags.state(DevFlagRegistry.showSkeletons).value)
    }

    @Test
    fun aReleaseBuildNeverTurnsAFlagOn() {
        val flags = DevFlags(enabled = false)

        flags.set(DevFlagRegistry.showSkeletons, true)

        assertFalse(flags.state(DevFlagRegistry.showSkeletons).value)
    }

    @Test
    fun everyFlagHasItsOwnId() {
        val ids: List<String> = DevFlagRegistry.all.map { it.id }

        assertEquals(ids.distinct(), ids)
    }

    @Test
    fun theGateIsClosedOnlyWhileSkeletonsAreShown() = runTest {
        val flags = DevFlags(enabled = true)
        val gate = SkeletonGate(flags)

        assertFalse(gate.held.value)
        flags.set(DevFlagRegistry.showSkeletons, true)
        assertTrue(gate.held.value)
        flags.set(DevFlagRegistry.showSkeletons, false)
        gate.awaitOpen()
        assertFalse(gate.held.value)
    }

    @Test
    fun theReleaseGateAlwaysReadsOpen() {
        val gate = SkeletonGate(DevFlags(enabled = false))

        assertFalse(gate.held.value)
    }

    @Test
    fun theDeveloperSectionListsEveryFlagWithItsState() {
        val flags = DevFlags(enabled = true)
        flags.set(DevFlagRegistry.showSkeletons, true)

        val items: List<DevFlagToggle> = flags.toggles()

        assertEquals(DevFlagRegistry.all.map { it.id }, items.map { it.flag.id })
        assertTrue(items.single { it.flag == DevFlagRegistry.showSkeletons }.checked)
    }
}
