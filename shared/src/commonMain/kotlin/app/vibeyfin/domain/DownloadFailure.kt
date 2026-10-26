package app.vibeyfin.domain

/** Why a download stopped and needs the user. Stored by [name] in the database. */
enum class DownloadFailure {
    /** The server doesn't let this user download. */
    Forbidden,

    /** The device has no room for it. */
    NoSpace,

    /** It would go over the profile's storage limit. */
    StorageLimit,

    /** The profile's token was rejected: it is locked until the user signs in again. */
    SignIn,

    /** The item is no longer on the server. */
    NotOnServer,

    /** Anything else that cannot be put right by waiting. */
    Unknown,
    ;

    companion object {
        fun fromCode(code: String?): DownloadFailure? = entries.firstOrNull { it.name == code }
    }
}
