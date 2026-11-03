package app.vibeyfin.ui.whoswatching

import app.vibeyfin.domain.model.Profile
import kotlin.test.Test
import kotlin.test.assertEquals

class ProfileOrderTest {
    private fun profile(id: String) = Profile(userId = id, name = id, hasSession = true, needsPassword = false)

    @Test
    fun theOpenedProfileKeepsItsPlace() {
        val shown = listOf(profile("a"), profile("b"))

        assertEquals(listOf("a", "b"), listOf(profile("b"), profile("a")).inOrderOf(shown).map { it.userId })
    }

    @Test
    fun profilesNotShownBeforeGoLast() {
        val shown = listOf(profile("a"), profile("b"))

        assertEquals(listOf("a", "b", "c"), listOf(profile("c"), profile("b"), profile("a")).inOrderOf(shown).map { it.userId })
    }
}
