package app.vibeyfin.ui.common

import app.vibeyfin.domain.ProfileSettings
import kotlinx.coroutines.flow.first

/** The one kids check: what a kids profile's menus leave out, its actions refuse too, so a stale tap cannot reach them. */
class KidsGuard(private val profileSettings: ProfileSettings) {
    suspend fun isKidsProfile(): Boolean = profileSettings.kidsProfile.first() == true

    suspend fun unlessKids(action: suspend () -> Unit) {
        if (!isKidsProfile()) action()
    }
}
