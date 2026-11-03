package app.vibeyfin.data.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.parameter
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.JsonObject
import kotlin.coroutines.CoroutineContext

/**
 * The calls we make to one Jellyfin server at [baseUrl]. With a [token] they are signed in.
 *
 * Every call returns an [ApiResult]; nothing here throws for a network or HTTP problem. Further
 * endpoint groups (library, playback) are extensions in their own files and use [get] and [post].
 *
 * Reads that are safe to repeat opt in to [retries]; sign-in and anything else that changes state
 * never does.
 */
class JellyfinApi(
    internal val http: HttpClient,
    val baseUrl: String,
    internal val token: String? = null,
    private val unauthorized: UnauthorizedEvents? = null,
    private val retries: RetryPolicy = RetryPolicy.Default,
    private val reachability: ServerReachability? = null,
    private val decodeOn: CoroutineContext = Dispatchers.Default,
) {
    /** The same server, signed in with [token]. */
    fun withToken(token: String?) = JellyfinApi(http, baseUrl, token, unauthorized, retries, reachability, decodeOn)

    /** `GET /System/Info/Public`: which server answers at this address. */
    suspend fun publicSystemInfo(): ApiResult<PublicSystemInfoDto> = get("/System/Info/Public")

    /** `GET /Users/Public`: the users the server lists on its sign-in screen. Needs no sign-in. */
    suspend fun publicUsers(): ApiResult<List<UserDto>> = get("/Users/Public")

    /** `POST /Users/AuthenticateByName`. A wrong name or password is [ApiError.Unauthorized]. */
    suspend fun authenticateByName(username: String, password: String): ApiResult<AuthenticationResultDto> = post("/Users/AuthenticateByName", AuthenticateByNameDto(username, password))

    /** `GET /QuickConnect/Enabled`. */
    suspend fun quickConnectEnabled(): ApiResult<Boolean> = get("/QuickConnect/Enabled")

    /** `POST /QuickConnect/Initiate`: starts a sign-in that another signed-in device approves. */
    suspend fun quickConnectInitiate(): ApiResult<QuickConnectResultDto> = send(HttpMethod.Post, "/QuickConnect/Initiate")

    /** `GET /QuickConnect/Connect`: whether the code has been approved yet. */
    suspend fun quickConnectStatus(secret: String): ApiResult<QuickConnectResultDto> = get("/QuickConnect/Connect", mapOf("secret" to secret))

    /** `POST /Users/AuthenticateWithQuickConnect`, once the code has been approved. */
    suspend fun authenticateWithQuickConnect(secret: String): ApiResult<AuthenticationResultDto> = post("/Users/AuthenticateWithQuickConnect", QuickConnectRequestDto(secret))

    /** `GET /Users/Me`: the signed-in user. A rejected token is [ApiError.Unauthorized]. */
    suspend fun me(): ApiResult<UserDto> = get("/Users/Me")

    /** `GET /Localization/ParentalRatings`: the rating names the server knows, each with its numeric level. */
    suspend fun parentalRatings(): ApiResult<List<ParentalRatingDto>> = get("/Localization/ParentalRatings")

    /** `GET /Localization/Cultures`: the languages the server knows, as it names them on tracks. */
    suspend fun cultures(): ApiResult<List<CultureDto>> = get("/Localization/Cultures", retry = true)

    /**
     * `POST /Users/Configuration`: replaces the whole configuration of user [userId], so [configuration] must be all of it.
     * A user may change their own; another user's needs an administrator ([ApiError.Forbidden] otherwise).
     */
    suspend fun updateUserConfiguration(userId: String, configuration: JsonObject): ApiResult<Unit> = post<JsonObject, Unit>("/Users/Configuration", configuration, mapOf("userId" to userId))

    /** `POST /Sessions/Logout`: revokes this session's token on the server. */
    suspend fun logout(): ApiResult<Unit> = execute({ }) {
        method = HttpMethod.Post
        url("$baseUrl/Sessions/Logout")
    }

    /** The URL of a user's avatar, for an image loader. */

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
        url("$baseUrl$path")
        query.forEach { (key, value) -> parameter(key, value) }
    }

    internal suspend inline fun <reified B : Any, reified T> post(
        path: String,
        body: B,
        query: Map<String, String> = emptyMap(),
    ): ApiResult<T> = execute({ it.body<T>() }) {
        method = HttpMethod.Post
        url("$baseUrl$path")
        query.forEach { (key, value) -> parameter(key, value) }
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private val call = ApiCall(
        http,
        retries,
        prepare = { token?.let { attributes.put(TokenAttribute, it) } },
        onAnswer = ::answered,
        onNoAnswer = { if (token != null) reachability?.failed() },
        decodeOn = decodeOn,
    )

    // Something answered: the server is there, whatever it said. And the one place a rejected token is noticed: a 401 on a call that
    // carried one.
    private fun answered(status: Int) {
        if (token == null) return
        reachability?.answered()
        if (status == 401) unauthorized?.report(token, baseUrl)
    }

    /**
     * Sends the request and turns the outcome into an [ApiResult]; [decode] reads a success body.
     * With [retry] a failure that may pass ([isRetryable]) is tried again, up to [retries] in all.
     */
    internal suspend fun <T> execute(
        decode: suspend (HttpResponse) -> T,
        retry: Boolean = false,
        configure: HttpRequestBuilder.() -> Unit,
    ): ApiResult<T> = call.execute(decode, retry, configure)

    /**
     * Like [execute] for a response too big to hold in memory: [read] consumes the body as it arrives, inside the call.
     * Never retried, since [read] has already used what it got; the caller resumes with a range. A failure of [read] itself,
     * thrown as [SinkFailure], is not a network problem and goes on to the caller.
     */
    internal suspend fun <T> stream(
        configure: HttpRequestBuilder.() -> Unit,
        read: suspend (HttpResponse) -> T,
    ): ApiResult<T> = try {
        http.prepareRequest {
            configure()
            token?.let { attributes.put(TokenAttribute, it) }
        }.execute { response ->
            val status = response.status.value
            answered(status)
            if (status !in 200..299) ApiResult.Failure(errorFor(status)) else ApiResult.Success(read(response))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: SinkFailure) {
        throw e
    } catch (_: Exception) {
        if (token != null) reachability?.failed()
        ApiResult.Failure(ApiError.Network)
    }
}

/** Builds [JellyfinApi] instances that share one HTTP client (and so its connections). */
class JellyfinApiFactory(
    private val http: HttpClient,
    private val unauthorized: UnauthorizedEvents? = null,
    private val retries: RetryPolicy = RetryPolicy.Default,
    private val reachability: ServerReachability? = null,
    private val decodeOn: CoroutineContext = Dispatchers.Default,
) {
    fun create(baseUrl: String, token: String? = null) = JellyfinApi(http, baseUrl, token, unauthorized, retries, reachability, decodeOn)
}
