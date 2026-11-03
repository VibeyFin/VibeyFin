package app.vibeyfin.data.seerr

import app.vibeyfin.data.api.ApiCall
import app.vibeyfin.data.api.ApiError
import app.vibeyfin.data.api.ApiResult
import app.vibeyfin.data.api.RetryPolicy
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

/** A successful sign-in: the Seerr user and the session cookie's value, which every later call sends. */
data class SeerrSignIn(val user: SeerrUserDto, val session: String)

/**
 * The calls we make to one Seerr at [baseUrl] (its root, such as `https://requests.example.org`; the API is under
 * `/api/v1`). With a [session] they are signed in.
 *
 * Every call returns an [ApiResult]; nothing here throws for a network or HTTP problem. A 401 is just
 * [ApiError.Unauthorized]: unlike Jellyfin's client nothing else hears of it, so Seerr can never lock a Jellyfin profile.
 * The catalogue and request calls are extensions in their own files.
 */
class SeerrApi(
    internal val http: HttpClient,
    val baseUrl: String,
    internal val session: String? = null,
    private val retries: RetryPolicy = RetryPolicy.Default,
    private val decodeOn: CoroutineContext = Dispatchers.Default,
) {
    /** The same Seerr, signed in with [session]. */
    fun withSession(session: String?) = SeerrApi(http, baseUrl, session, retries, decodeOn)

    /** `GET /status`: answers on any Seerr, signed in or not. */
    suspend fun status(): ApiResult<SeerrStatusDto> = get("/status")

    /** `GET /settings/public`. */
    suspend fun publicSettings(): ApiResult<SeerrPublicSettingsDto> = get("/settings/public")

    /**
     * `POST /auth/jellyfin/quickconnect/initiate`: Seerr starts a Quick Connect sign-in on its Jellyfin server. Fails with
     * [ApiError.Server] when Quick Connect is off there.
     */
    suspend fun quickConnectInitiate(): ApiResult<SeerrQuickConnectDto> = send(HttpMethod.Post, "/auth/jellyfin/quickconnect/initiate")

    /** `POST /auth/jellyfin/quickconnect/authenticate`, once the code is approved on Jellyfin. */
    suspend fun quickConnectAuthenticate(secret: String): ApiResult<SeerrSignIn> = signIn("/auth/jellyfin/quickconnect/authenticate", SeerrSecretDto(secret))

    /** `POST /auth/jellyfin` with the Jellyfin user's name and password. A wrong password is [ApiError.Unauthorized] or [ApiError.Forbidden]. */
    suspend fun signInWithPassword(username: String, password: String): ApiResult<SeerrSignIn> = signIn("/auth/jellyfin", SeerrPasswordDto(username, password))

    /** `GET /auth/me`: the signed-in user. An expired session is [ApiError.Unauthorized]. */
    suspend fun me(): ApiResult<SeerrUserDto> = get("/auth/me")

    /** `POST /auth/logout`: ends the session on Seerr. */
    suspend fun logout(): ApiResult<Unit> = execute({ }) {
        method = HttpMethod.Post
        url(endpoint("/auth/logout"))
    }

    internal fun endpoint(path: String) = "$baseUrl$API_PATH$path"

    internal suspend inline fun <reified T> get(
        path: String,
        query: Map<String, String> = emptyMap(),
        retry: Boolean = false,
    ): ApiResult<T> = send(HttpMethod.Get, path, query, retry)

    internal suspend inline fun <reified T> send(
        method: HttpMethod,
        path: String,
        query: Map<String, String> = emptyMap(),
        retry: Boolean = false,
    ): ApiResult<T> = execute({ it.body<T>() }, retry) {
        this.method = method
        url(endpoint(path))
        query.forEach { (key, value) -> parameter(key, value) }
    }

    internal suspend inline fun <reified B : Any, reified T> post(path: String, body: B): ApiResult<T> = execute({ it.body<T>() }) {
        method = HttpMethod.Post
        url(endpoint(path))
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private suspend inline fun <reified B : Any> signIn(path: String, body: B): ApiResult<SeerrSignIn> = execute({ response ->
        val session = response.headers.getAll(HttpHeaders.SetCookie).orEmpty().firstNotNullOfOrNull(::sessionCookie)
            ?: throw IllegalStateException("Seerr signed in without a session cookie")
        SeerrSignIn(response.body(), session)
    }) {
        method = HttpMethod.Post
        url(endpoint(path))
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    // Seerr's session is a cookie, sent by hand. Nothing hears of a 401 or of reachability: a Seerr 401 can't lock a Jellyfin profile.
    private val call = ApiCall(http, retries, prepare = { session?.let { header(HttpHeaders.Cookie, "$SESSION_COOKIE=$it") } }, decodeOn = decodeOn)

    /**
     * Sends the request and turns the outcome into an [ApiResult]; [decode] reads a success body (and a failure to read it
     * is [ApiError.BadResponse]). With [retry] a failure that may pass is tried again, up to [retries] in all.
     */
    internal suspend fun <T> execute(
        decode: suspend (HttpResponse) -> T,
        retry: Boolean = false,
        configure: HttpRequestBuilder.() -> Unit,
    ): ApiResult<T> = call.execute(decode, retry, configure)

    internal companion object {
        const val API_PATH = "/api/v1"
        const val SESSION_COOKIE = "connect.sid"

        /** The value of the session cookie in one `Set-Cookie` header, kept encoded as Seerr sent it; null for another cookie. */
        fun sessionCookie(setCookie: String): String? {
            val pair = setCookie.substringBefore(';').trim()
            if (pair.substringBefore('=') != SESSION_COOKIE) return null
            return pair.substringAfter('=').takeIf { it.isNotEmpty() }
        }
    }
}

/** Builds [SeerrApi] instances that share one HTTP client. */
class SeerrApiFactory(
    private val http: HttpClient,
    private val retries: RetryPolicy = RetryPolicy.Default,
    private val decodeOn: CoroutineContext = Dispatchers.Default,
) {
    fun create(baseUrl: String, session: String? = null) = SeerrApi(http, baseUrl, session, retries, decodeOn)
}
