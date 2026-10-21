package app.vibeyfin.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BingeCounterTest {
    @Test
    fun `asks after three auto advances`() {
        val counter = BingeCounter()
        repeat(2) { counter.autoAdvanced() }
        assertFalse(counter.shouldAsk(3))
        counter.autoAdvanced()
        assertTrue(counter.shouldAsk(3))
    }

    @Test
    fun `interaction resets the count`() {
        val counter = BingeCounter()
        repeat(3) { counter.autoAdvanced() }
        counter.interacted()
        assertEquals(0, counter.count.value)
        assertFalse(counter.shouldAsk(3))
    }

    @Test
    fun `threshold 0 never asks`() {
        val counter = BingeCounter()
        repeat(10) { counter.autoAdvanced() }
        assertFalse(counter.shouldAsk(0))
    }

    @Test
    fun `count survives being read from a second consumer`() {
        val counter = BingeCounter()
        counter.autoAdvanced()
        counter.autoAdvanced()
        // A new playback view model gets the same singleton and sees the same count.
        val secondConsumer: BingeCounter = counter
        assertEquals(2, secondConsumer.count.value)
        secondConsumer.autoAdvanced()
        assertEquals(3, counter.count.value)
        counter.reset()
        assertEquals(0, secondConsumer.count.value)
    }
}
