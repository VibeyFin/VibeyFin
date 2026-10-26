package app.vibeyfin.app.notifications

import android.content.Intent
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NewTitleTapGateTest {
    private val server = "99136377524646379aa1b9f1b4093546"
    private val user = "857bf4861f3a49358ea50facf8e70184"
    private val item = "bc520674a605317bb8f834d93376d75d"

    /** One saved profile with notifications on, notified about [item] only. */
    private val gate = NewTitleTapGate(
        notificationsOn = { s, u -> s == server && u == user },
        wasNotified = { s, u, i -> s == server && u == user && i == item },
    )

    private fun accepts(tap: NewTitleTap) = runBlocking { gate.accept(tap) }

    @Test
    fun `a legitimate tap is accepted`() {
        assertTrue(accepts(NewTitleTap(server, user, item)))
    }

    @Test
    fun `a dashed id of the right shape is accepted`() {
        val dashed = "bc520674-a605-317b-b8f8-34d93376d75d"
        val gate = NewTitleTapGate(notificationsOn = { _, _ -> true }, wasNotified = { _, _, i -> i == dashed })
        assertTrue(runBlocking { gate.accept(NewTitleTap(server, user, dashed)) })
    }

    @Test
    fun `a foreign server is dropped`() {
        assertFalse(accepts(NewTitleTap("0123456789abcdef0123456789abcdef", user, item)))
    }

    @Test
    fun `an unknown user is dropped`() {
        assertFalse(accepts(NewTitleTap(server, "fedcba9876543210fedcba9876543210", item)))
    }

    @Test
    fun `an item the profile was never notified about is dropped`() {
        assertFalse(accepts(NewTitleTap(server, user, "21071a848fb9f9037601e288192925a9")))
    }

    @Test
    fun `a malformed id is dropped before any lookup`() {
        var lookups = 0
        val counting = NewTitleTapGate(notificationsOn = { _, _ -> lookups++; true }, wasNotified = { _, _, _ -> lookups++; true })
        for (bad in listOf("../Users/$user", "$item/Similar", "", "xyz", "${item}0")) {
            assertFalse(runBlocking { counting.accept(NewTitleTap(server, user, bad)) }, bad)
        }
        assertFalse(runBlocking { counting.accept(NewTitleTap("srv-1", user, item)) })
        assertTrue(lookups == 0)
    }

    @Test
    fun `a launch replayed from recents is not read as a tap`() {
        assertTrue(NewTitleTap.isReplay(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY or Intent.FLAG_ACTIVITY_NEW_TASK))
        assertFalse(NewTitleTap.isReplay(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }

    @Test
    fun `each notification has its own tap identity`() {
        val first = NewTitleTap.uri(server, user, item)
        assertEquals(first, NewTitleTap.uri(server, user, item))
        assertNotEquals(first, NewTitleTap.uri(server, user, "21071a848fb9f9037601e288192925a9"))
        assertNotEquals(first, NewTitleTap.uri(server, "fedcba9876543210fedcba9876543210", item))
        assertEquals("vibeyfin://new-title/a%2Fb/$user/$item", NewTitleTap.uri("a/b", user, item))
    }
}
