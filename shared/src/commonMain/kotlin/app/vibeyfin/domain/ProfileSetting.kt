package app.vibeyfin.domain

import app.vibeyfin.data.db.AccountDao
import app.vibeyfin.domain.model.Account
import app.vibeyfin.domain.model.ProfileRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * One setting of a profile: the value it has until one is saved, which values it accepts, and how the DAO reads and writes it.
 * [write] is null for a setting that is only read here because its writes are separate (the PIN gates).
 */
class ProfileSetting<T : Any>(
    val default: T,
    private val accepts: (T) -> Boolean = { true },
    private val read: (AccountDao, Account) -> Flow<T?>,
    private val write: (suspend (AccountDao, Account, T) -> Unit)? = null,
) {
    fun accepts(value: T): Boolean = accepts.invoke(value)

    /** The saved value, or [default] when none is saved or the saved one is not [accepts]ed. */
    internal fun observe(dao: AccountDao, account: Account): Flow<T> = read(dao, account).map { it?.takeIf(accepts) ?: default }

    internal suspend fun write(dao: AccountDao, account: Account, value: T) {
        write?.invoke(dao, account, value)
    }
}

/** What [ProfileSettings.values] read for [profile] so far; a setting not read yet is [ProfileSetting.default] and null from [readOrNull]. */
class ProfileValues internal constructor(
    /** The profile these were read for; null when nobody is signed in. */
    val profile: ProfileRef?,
    private val read: Map<ProfileSetting<*>, Any> = emptyMap(),
) {
    val hasProfile: Boolean get() = profile != null

    @Suppress("UNCHECKED_CAST")
    operator fun <T : Any> get(setting: ProfileSetting<T>): T = read[setting] as T? ?: setting.default

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> readOrNull(setting: ProfileSetting<T>): T? = read[setting] as T?

    internal fun <T : Any> with(setting: ProfileSetting<T>, value: T) = ProfileValues(profile, read + (setting to value))

    /** These when they were read for [current]; otherwise nothing is read yet, so one profile's values never show under another. */
    fun shownFor(current: ProfileRef?): ProfileValues = if (profile == current) this else ProfileValues(current)
}
