package app.vibeyfin.domain

import app.vibeyfin.data.api.ApiError
import app.vibeyfin.data.api.ApiResult
import app.vibeyfin.data.api.JellyfinApi
import app.vibeyfin.data.api.authorizeQuickConnect
import app.vibeyfin.data.seerr.SeerrApi
import app.vibeyfin.data.seerr.SeerrApiFactory
import app.vibeyfin.data.seerr.SeerrSignIn
import app.vibeyfin.domain.model.SeerrFeatures
import app.vibeyfin.domain.model.SeerrUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Why the profile isn't signed in to Seerr just now. */
enum class SeerrProblem {
    /** Seerr (or Jellyfin, to approve the sign-in) didn't answer. */
    Unreachable,

    /** This Seerr belongs to a different Jellyfin server. */
    WrongServer,

    /** Seerr refused this Jellyfin user, for example because new Jellyfin sign-ins are turned off on it. */
    NoAccess,

    /** Anything else Seerr answered with. */
    Failed,
}

/** Seerr as the signed-in profile sees it. Everything of Seerr in the app shows only while it is [Ready]. */
sealed interface SeerrState {
    /** Nothing of Seerr shows at all: nobody is signed in, or the profile is a kids profile (kids can't request). */
    data object Hidden : SeerrState

    /** The server has no Seerr. Settings offers to connect one. */
    data object NotSet : SeerrState

    /** The profile turned Seerr off; the server keeps its address for the other profiles. */
    data class Off(val address: String) : SeerrState

    data class Connecting(val address: String) : SeerrState

    /** Signed in. Seerr calls go through [SeerrConnection.call]; nothing else holds the session. */
    data class Ready(
        val address: String,
        val user: SeerrUser,
        val features: SeerrFeatures,
        /** The session came from the Jellyfin password, so it is asked again when Seerr ends the session. */
        val signedInWithPassword: Boolean,
        internal val serverId: String,
        internal val userId: String,
        internal val api: SeerrApi,
    ) : SeerrState

    /** Quick Connect is off on the Jellyfin server, so the profile signs in once with its Jellyfin password. */
    data class NeedsPassword(val address: String) : SeerrState

    /** Signing in didn't work this time. [SeerrConnection.retry] tries again. */
    data class Unavailable(val address: String, val problem: SeerrProblem) : SeerrState
}

/** The answer to [SeerrConnection.connect]. */
enum class SeerrConnectResult {
    Connected,

    /** The address is saved, but Quick Connect is off: the profile signs in with its password ([SeerrState.NeedsPassword]). */
    NeedsPassword,

    /** Nothing answered at the address. */
    Unreachable,

    /** Something answered, but it isn't a Seerr that is set up. */
    NotSeerr,
    WrongServer,
    NoAccess,
    Failed,
}

/** The answer to [SeerrConnection.signInWithPassword]. */
enum class SeerrPasswordResult { SignedIn, WrongPassword, Unreachable, WrongServer, Failed }

/**
 * Owns the signed-in profile's Seerr session as a [StateFlow], the way [ConnectionManager] owns its Jellyfin one, and signs
 * the profile in to Seerr without asking: it starts a Quick Connect on Seerr and approves the code itself with the profile's
 * Jellyfin token, so no password is needed. Only the session cookie is kept, sealed in the token vault.
 *
 * A Seerr 401 never touches the Jellyfin session: [call] signs in to Seerr again, once, and tries the call again. A 401
 * from Jellyfin while approving the code goes through Jellyfin's own path ([ConnectionManager] locks the profile).
 *
 * Seerr's address belongs to the server; turning Seerr off ([disconnect]) belongs to the profile. A kids profile never signs
 * in, and a profile that turns kids mode on is signed out.
 */
class SeerrConnection(
    private val connection: ConnectionManager,
    private val store: SeerrStore,
    private val factory: SeerrApiFactory,
    private val scope: CoroutineScope,
) {
    private data class Key(val serverId: String, val userId: String, val kids: Boolean)

    private enum class Outcome { SignedIn, NeedsPassword, Unreachable, WrongServer, NoAccess, Failed, Gone }

    private val _state = MutableStateFlow<SeerrState>(SeerrState.Hidden)
    val state: StateFlow<SeerrState> = _state.asStateFlow()

    // One sign-in at a time, and state changes in order.
    private val lock = Mutex()

    init {
        scope.launch {
            profiles().collect { key -> lock.withLock { resolve(key) } }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun profiles() = connection.state
        .map { it.accountOrNull()?.let { account -> account.serverId to account.userId } }
        .distinctUntilChanged()
        .flatMapLatest { profile ->
            if (profile == null) flowOf(null) else store.observeKidsProfile(profile.first, profile.second).map { Key(profile.first, profile.second, it) }
        }
        .distinctUntilChanged()

    /**
     * Links the server to the Seerr at [input] (an address as typed: `https://` is assumed when left out) and signs this
     * profile in. The address is saved only once it is known to be this server's Seerr, or when only the password step is
     * left. Turns Seerr back on for a profile that had turned it off.
     */
    suspend fun connect(input: String): SeerrConnectResult {
        val key = currentKey()?.takeIf { !it.kids } ?: return SeerrConnectResult.Failed
        var address = normalizeSeerrAddress(input) ?: return SeerrConnectResult.NotSeerr
        var seerr = factory.create(address)
        var status = seerr.status()
        // Typed without a scheme and nothing answers on https: a stock Seerr on the LAN ("192.168.1.10:5055") speaks plain http
        if (status is ApiResult.Failure && status.error == ApiError.Network && !input.contains("://")) {
            val plain = "http://" + address.removePrefix("https://")
            val second = factory.create(plain).status()
            if (second is ApiResult.Success) {
                address = plain
                seerr = factory.create(plain)
                status = second
            }
        }
        if (status is ApiResult.Failure) {
            return if (status.error == ApiError.Network || status.error is ApiError.Server) SeerrConnectResult.Unreachable else SeerrConnectResult.NotSeerr
        }
        val settings = seerr.publicSettings()
        if (settings !is ApiResult.Success || !settings.value.initialized) return SeerrConnectResult.NotSeerr
        if (settings.value.mediaServerType != JELLYFIN_SERVER) return SeerrConnectResult.WrongServer
        return lock.lockedIn(scope) {
            val before = _state.value
            _state.value = SeerrState.Connecting(address)
            val (outcome, signIn) = signIn(key, address)
            when (outcome) {
                Outcome.SignedIn -> {
                    store.setAddress(key.serverId, address)
                    store.setOff(key.serverId, key.userId, false)
                    ready(key, address, signIn!!)
                    SeerrConnectResult.Connected
                }

                Outcome.NeedsPassword -> {
                    store.setAddress(key.serverId, address)
                    store.setOff(key.serverId, key.userId, false)
                    _state.value = SeerrState.NeedsPassword(address)
                    SeerrConnectResult.NeedsPassword
                }

                else -> {
                    _state.value = before
                    when (outcome) {
                        Outcome.Unreachable -> SeerrConnectResult.Unreachable
                        Outcome.WrongServer -> SeerrConnectResult.WrongServer
                        Outcome.NoAccess -> SeerrConnectResult.NoAccess
                        else -> SeerrConnectResult.Failed
                    }
                }
            }
        }
    }

    /** Signs the profile in with its Jellyfin password, when Quick Connect is off. The password is never kept. */
    suspend fun signInWithPassword(password: String): SeerrPasswordResult = lock.lockedIn(scope) {
        val key = currentKey()?.takeIf { !it.kids } ?: return@lockedIn SeerrPasswordResult.Failed
        val name = connection.asAccount<String?>(null) { it.name } ?: return@lockedIn SeerrPasswordResult.Failed
        val address = store.address(key.serverId) ?: return@lockedIn SeerrPasswordResult.Failed
        when (val result = factory.create(address).signInWithPassword(name, password)) {
            is ApiResult.Failure -> when (result.error) {
                ApiError.Unauthorized, ApiError.Forbidden -> SeerrPasswordResult.WrongPassword
                ApiError.Network -> SeerrPasswordResult.Unreachable
                else -> SeerrPasswordResult.Failed
            }

            is ApiResult.Success -> if (!belongsTo(result.value, key)) {
                factory.create(address, result.value.session).logout()
                SeerrPasswordResult.WrongServer
            } else {
                store.setOff(key.serverId, key.userId, false)
                ready(key, address, result.value, viaPassword = true)
                SeerrPasswordResult.SignedIn
            }
        }
    }

    /** Turns Seerr off for this profile: ends its session and stops signing it in. Other profiles keep Seerr. */
    suspend fun disconnect() = lock.lockedIn(scope) {
        val key = currentKey() ?: return@lockedIn
        (_state.value as? SeerrState.Ready)?.api?.logout()
        store.clearSession(key.serverId, key.userId)
        store.setOff(key.serverId, key.userId, true)
        resolve(key)
    }

    /** Turns Seerr back on for a profile that turned it off, at the server's address, and signs it in. */
    suspend fun reconnect() = lock.lockedIn(scope) {
        val key = currentKey() ?: return@lockedIn
        store.setOff(key.serverId, key.userId, false)
        resolve(key)
    }

    /** Tries signing in again after [SeerrState.Unavailable]; does nothing in any other state. */
    suspend fun retry() = lock.lockedIn(scope) {
        if (_state.value is SeerrState.Unavailable) resolve(currentKey())
    }

    /**
     * Runs [block] signed in to Seerr. A 401 means the session ended on Seerr: the profile signs in again by itself, once,
     * and [block] runs again. Not [SeerrState.Ready] is [ApiError.Unauthorized] without a call.
     */
    suspend fun <T> call(block: suspend (SeerrApi, SeerrUser) -> ApiResult<T>): ApiResult<T> {
        val ready = _state.value as? SeerrState.Ready ?: return ApiResult.Failure(ApiError.Unauthorized)
        val result = block(ready.api, ready.user)
        if (result !is ApiResult.Failure || result.error != ApiError.Unauthorized) return result
        val renewed = renew(ready) ?: return result
        return block(renewed.api, renewed.user)
    }

    // Signs in again after Seerr rejected [stale]'s session, unless another call already did. The old Ready stays while it does,
    // so the Seerr rows and groups aren't torn down for an internal renewal; calls that hit the same 401 wait here for the new one.
    private suspend fun renew(stale: SeerrState.Ready): SeerrState.Ready? = lock.lockedIn(scope) {
        val now = _state.value
        if (now !is SeerrState.Ready) return@lockedIn null
        if (now.api !== stale.api) return@lockedIn now
        val key = Key(now.serverId, now.userId, kids = false)
        store.clearSession(key.serverId, key.userId)
        signInAndSettle(key, now.address)
        _state.value as? SeerrState.Ready
    }

    // Works out the state for [key] from what is saved, signing in when there is no session. Runs under [lock].
    private suspend fun resolve(key: Key?) {
        lastKey = key
        // The profile that was signed in to Seerr turned kids, or is gone with its saved session already wiped (a server removed):
        // end that session on Seerr too, so it doesn't stay valid there. A switch to another profile keeps it. Best effort. A Jellyfin
        // sign-out isn't caught here yet: the state changes before AccountRepository.expire wipes the session.
        val previous = _state.value as? SeerrState.Ready
        if (previous != null) {
            val same = key?.serverId == previous.serverId && key.userId == previous.userId
            val dropped = if (same) key.kids else store.session(previous.serverId, previous.userId) == null
            if (dropped) scope.launch { previous.api.logout() }
        }
        if (key == null) {
            _state.value = SeerrState.Hidden
            return
        }
        if (key.kids) {
            store.clearSession(key.serverId, key.userId)
            _state.value = SeerrState.Hidden
            return
        }
        val address = store.address(key.serverId)
        when {
            address == null -> _state.value = SeerrState.NotSet

            store.isOff(key.serverId, key.userId) -> _state.value = SeerrState.Off(address)

            else -> {
                val current = _state.value
                if (current is SeerrState.Ready && current.isFor(key, address)) return
                _state.value = SeerrState.Connecting(address)
                open(key, address)
            }
        }
    }

    // Uses the saved session if Seerr still takes it, otherwise signs in.
    private suspend fun open(key: Key, address: String) {
        val saved = store.session(key.serverId, key.userId)
        if (saved != null) {
            val api = factory.create(address, saved.session)
            when (val me = api.me()) {
                is ApiResult.Success -> {
                    if (connection.isSignedInAs(key.serverId, key.userId)) _state.value = SeerrState.Ready(address, me.value.toUser(), features(api), saved.viaPassword, key.serverId, key.userId, api)
                    return
                }

                is ApiResult.Failure -> if (me.error != ApiError.Unauthorized) {
                    // Seerr is down, not the session: keep it for next time.
                    _state.value = SeerrState.Unavailable(address, SeerrProblem.Unreachable)
                    return
                }
            }
            store.clearSession(key.serverId, key.userId)
        }
        signInAndSettle(key, address)
    }

    private suspend fun signInAndSettle(key: Key, address: String) {
        val (outcome, signIn) = signIn(key, address)
        _state.value = when (outcome) {
            Outcome.SignedIn -> return ready(key, address, signIn!!)

            Outcome.NeedsPassword -> SeerrState.NeedsPassword(address)

            Outcome.Unreachable -> SeerrState.Unavailable(address, SeerrProblem.Unreachable)

            Outcome.WrongServer -> SeerrState.Unavailable(address, SeerrProblem.WrongServer)

            Outcome.NoAccess -> SeerrState.Unavailable(address, SeerrProblem.NoAccess)

            Outcome.Failed -> SeerrState.Unavailable(address, SeerrProblem.Failed)

            // The profile changed or its Jellyfin session ended meanwhile; the new state arrives through [profiles].
            Outcome.Gone -> SeerrState.Hidden
        }
    }

    // The silent sign-in: Seerr starts a Quick Connect on its Jellyfin, this profile approves the code on its own Jellyfin
    // with its token, and Seerr opens a session. A code our server doesn't know means Seerr talks to another server.
    private suspend fun signIn(key: Key, address: String): Pair<Outcome, SeerrSignIn?> {
        val jellyfin = jellyfinFor(key) ?: return Outcome.Gone to null
        when (val enabled = jellyfin.quickConnectEnabled()) {
            is ApiResult.Success -> if (!enabled.value) return Outcome.NeedsPassword to null
            is ApiResult.Failure -> return (if (enabled.error == ApiError.Network) Outcome.Unreachable else Outcome.Failed) to null
        }
        val seerr = factory.create(address)
        val code = when (val started = seerr.quickConnectInitiate()) {
            is ApiResult.Success -> started.value
            is ApiResult.Failure -> return startFailure(started.error) to null
        }
        val approved = jellyfin.authorizeQuickConnect(code.code, key.userId)
        if (approved is ApiResult.Failure) return approvalFailure(approved.error) to null
        val signIn = when (val done = seerr.quickConnectAuthenticate(code.secret)) {
            is ApiResult.Success -> done.value

            is ApiResult.Failure -> return when (done.error) {
                ApiError.Forbidden -> Outcome.NoAccess
                ApiError.Network -> Outcome.Unreachable
                else -> Outcome.Failed
            } to null
        }
        if (!belongsTo(signIn, key)) {
            factory.create(address, signIn.session).logout()
            return Outcome.WrongServer to null
        }
        return Outcome.SignedIn to signIn
    }

    private fun startFailure(error: ApiError): Outcome = when (error) {
        ApiError.Network -> Outcome.Unreachable

        // Seerr's own Jellyfin refused to start one: Quick Connect is off there.
        is ApiError.Server -> Outcome.NeedsPassword

        else -> Outcome.Failed
    }

    private fun approvalFailure(error: ApiError): Outcome = when (error) {
        ApiError.NotFound -> Outcome.WrongServer

        ApiError.Network -> Outcome.Unreachable

        // The Jellyfin token was rejected: ConnectionManager locks the profile.
        ApiError.Unauthorized -> Outcome.Gone

        else -> Outcome.Failed
    }

    private fun SeerrState.Ready.isFor(key: Key, address: String) = serverId == key.serverId && userId == key.userId && this.address == address

    private suspend fun ready(key: Key, address: String, signIn: SeerrSignIn, viaPassword: Boolean = false) {
        store.saveSession(key.serverId, key.userId, signIn.session, signIn.user.id, viaPassword)
        val api = factory.create(address, signIn.session)
        // The profile changed meanwhile: its own resolve comes next under the lock and sets the state
        if (!connection.isSignedInAs(key.serverId, key.userId)) return
        _state.value = SeerrState.Ready(address, signIn.user.toUser(), features(api), viaPassword, key.serverId, key.userId, api)
    }

    private suspend fun features(api: SeerrApi): SeerrFeatures = when (val settings = api.publicSettings()) {
        is ApiResult.Success -> SeerrFeatures(movies4k = settings.value.movie4kEnabled, shows4k = settings.value.series4kEnabled)
        is ApiResult.Failure -> SeerrFeatures()
    }

    // The Seerr user must be this profile's Jellyfin user. An older Seerr that doesn't say is taken at its word.
    private fun belongsTo(signIn: SeerrSignIn, key: Key): Boolean {
        val theirs = signIn.user.jellyfinUserId ?: return true
        return sameJellyfinId(theirs, key.userId)
    }

    // The signed-in profile as [resolve] last saw it, kids flag included; null before it has seen one.
    private fun currentKey(): Key? = connection.asAccount(null) { account -> lastKey?.takeIf { it.serverId == account.serverId && it.userId == account.userId } }

    // Written only by [resolve], under [lock].
    private var lastKey: Key? = null

    private fun jellyfinFor(key: Key): JellyfinApi? = connection.asConnected(null) { connected ->
        connected.api.takeIf { connected.account.serverId == key.serverId && connected.account.userId == key.userId }
    }

    private companion object {
        const val JELLYFIN_SERVER = 2
    }
}

/** Jellyfin writes user ids with and without dashes; they name the same user either way. */
internal fun sameJellyfinId(a: String, b: String): Boolean = a.replace("-", "").equals(b.replace("-", ""), ignoreCase = true)

/**
 * An address as the user typed it, made into Seerr's root URL: `https://` when no scheme is given, without a trailing `/`
 * or `/api/v1`. Null when it can't be an address.
 */
fun normalizeSeerrAddress(input: String): String? {
    var address = input.trim().trimEnd('/')
    if (address.isEmpty() || address.any { it.isWhitespace() }) return null
    // The scheme in any case ("HTTP://..."), as browsers take it
    address = if (address.contains("://")) address.substringBefore("://").lowercase() + "://" + address.substringAfter("://") else "https://$address"
    if (!address.startsWith("http://") && !address.startsWith("https://")) return null
    address = address.removeSuffix(SeerrApi.API_PATH).trimEnd('/')
    val host = address.substringAfter("://").substringBefore('/').substringBefore(':')
    return address.takeIf { host.isNotEmpty() }
}

// Runs [block] under this lock in [scope], not the caller's: a screen closing (its scope cancelled) mid sign-in must not leave the
// state at Connecting with nobody to finish it. The caller still waits for the result.
private suspend fun <T> Mutex.lockedIn(scope: CoroutineScope, block: suspend () -> T): T = scope.async { withLock { block() } }.await()

// Whether the signed-in profile is still this one: a sign-in that finishes after a profile switch mustn't publish its Ready.
private fun ConnectionManager.isSignedInAs(serverId: String, userId: String): Boolean = asAccount(false) { it.serverId == serverId && it.userId == userId }
