package app.vibeyfin.domain

import app.vibeyfin.domain.model.Account
import app.vibeyfin.domain.model.Profile

/** A user the server lists on its own sign-in screen. Hidden users never appear. */
data class PublicUser(
    val id: String,
    val name: String,
    val imageTag: String?,
    val hasPassword: Boolean,
)

/** What the server told us on `/Users/Public`, plus the address that answered. */
data class PublicProfiles(
    val users: List<PublicUser>,
    val baseUrl: String,
    val quickConnectEnabled: Boolean,
)

/**
 * The tiles for "Who's watching?": profiles saved on this device merged with the server's public
 * users, keyed by user ID. Saved profiles come first, most recently used first, because that is how
 * [saved] is ordered. A saved profile the server doesn't list (a hidden user, or the server can't
 * be reached) is still shown and counts as needing a password.
 */
fun mergeProfiles(saved: List<Account>, public: PublicProfiles?): List<Profile> {
    val publicById = public?.users.orEmpty().associateBy { it.id }

    val fromSaved = saved.map { account ->
        val listed = publicById[account.userId]
        Profile(
            userId = account.userId,
            name = listed?.name ?: account.name,
            hasSession = account.hasSession,
            needsPassword = listed?.hasPassword ?: true,
            pinRequiredToOpen = account.pinRequiredToOpen,
        )
    }
    val savedIds = saved.mapTo(HashSet()) { it.userId }
    val others = public?.users.orEmpty().filter { it.id !in savedIds }.map { user ->
        Profile(
            userId = user.id,
            name = user.name,
            hasSession = false,
            needsPassword = user.hasPassword,
        )
    }
    return fromSaved + others
}
