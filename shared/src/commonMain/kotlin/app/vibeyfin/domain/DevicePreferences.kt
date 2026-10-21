package app.vibeyfin.domain

import app.vibeyfin.domain.model.ProfileRef
import kotlinx.coroutines.flow.Flow

/** Settings that belong to the device, not to a profile. Per-profile options live in the database. */
interface DevicePreferences {
    /** A UUID generated on first use and kept for the life of the install. */
    suspend fun deviceId(): String

    val lastServerId: Flow<String?>

    /** The profile that was open last, or null when none was or its server changed since. */
    val lastProfile: Flow<ProfileRef?>

    /** When on, "Who's watching?" shows on every start instead of reopening the last profile. */
    val alwaysAskWhoIsWatching: Flow<Boolean>

    /** Switching to a different server forgets the last profile, which belonged to the old one. */
    suspend fun setLastServer(serverId: String?)

    suspend fun setLastProfile(profile: ProfileRef)

    suspend fun setAlwaysAskWhoIsWatching(value: Boolean)
}
