package app.vibeyfin.domain

import app.vibeyfin.domain.model.Account
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class ProfilesTest {
    private fun saved(id: String, name: String, session: Boolean = true, tag: String? = null) = Account("srv-1", id, name, tag, session, Instant.fromEpochMilliseconds(0))

    private fun public(vararg users: PublicUser) = PublicProfiles(users.toList(), "http://nas:8096", quickConnectEnabled = false)

    @Test
    fun savedProfilesComeFirstThenTheServersOtherUsers() {
        val profiles = mergeProfiles(
            saved = listOf(saved("u2", "Sam"), saved("u1", "Alex")),
            public = public(PublicUser("u1", "Alex", null, true), PublicUser("u3", "Kim", null, false), PublicUser("u2", "Sam", null, true)),
        )

        assertEquals(listOf("u2", "u1", "u3"), profiles.map { it.userId })
    }

    @Test
    fun aUserIsOneTileWhetherSavedListedOrBoth() {
        val profiles = mergeProfiles(
            saved = listOf(saved("u1", "Old name")),
            public = public(PublicUser("u1", "New name", "tag", true)),
        )

        assertEquals(1, profiles.size)
        assertEquals("New name", profiles.single().name)
    }

    @Test
    fun aSavedSessionMeansNoPasswordIsAsked() {
        val tile = mergeProfiles(listOf(saved("u1", "Alex", session = true)), public(PublicUser("u1", "Alex", null, true))).single()

        assertTrue(tile.hasSession)
        assertFalse(tile.locked)
    }

    @Test
    fun anExpiredProfileKeepsItsTileAndIsLocked() {
        val tile = mergeProfiles(listOf(saved("u1", "Alex", session = false)), public(PublicUser("u1", "Alex", null, true))).single()

        assertTrue(tile.locked)
    }

    @Test
    fun aUserWithoutAPasswordIsNeverLocked() {
        val tile = mergeProfiles(emptyList(), public(PublicUser("u3", "Kim", null, hasPassword = false))).single()

        assertFalse(tile.needsPassword)
        assertFalse(tile.locked)
    }

    @Test
    fun aSavedProfileTheServerDoesNotListStillShowsAndNeedsAPassword() {
        val tile = mergeProfiles(listOf(saved("hidden", "Hidden", session = false)), public(PublicUser("u1", "Alex", null, true)))
            .first { it.userId == "hidden" }

        assertTrue(tile.needsPassword)
        assertTrue(tile.locked)
    }

    @Test
    fun whenTheServerCannotBeReachedSavedProfilesStillShow() {
        val profiles = mergeProfiles(listOf(saved("u1", "Alex", tag = "abc")), public = null)

        assertEquals("Alex", profiles.single().name)
    }
}
