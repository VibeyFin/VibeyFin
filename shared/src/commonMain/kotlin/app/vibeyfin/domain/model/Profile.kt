package app.vibeyfin.domain.model

/** One tile on "Who's watching?": a user on a server, whether or not this device has signed in as them. */
data class Profile(
    val userId: String,
    val name: String,
    /** This device holds a token for the profile, so tapping it signs in without a password. */
    val hasSession: Boolean,
    /** Signing in needs a password; false for a user the server says has none. */
    val needsPassword: Boolean,
    /** Opening the profile asks for its PIN first; the tile shows a lock badge. */
    val pinRequiredToOpen: Boolean = false,
) {
    /** Shown with a lock: there is no session and a password is needed to get one. */
    val locked: Boolean get() = !hasSession && needsPassword
}
