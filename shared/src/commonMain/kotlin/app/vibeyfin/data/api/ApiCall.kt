package app.vibeyfin.data.api

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Sends requests for one backend's API and turns each outcome into an [ApiResult]: the part Jellyfin's and Seerr's clients share.
 * What differs is passed in: [prepare] adds the credentials to every request, [onAnswer] hears the status of anything that answered
 * (Jellyfin reports a rejected token and that the server is there), and [onNoAnswer] hears that nothing did.
 */
internal class ApiCall(
    private val http: HttpClient,
    private val retries: RetryPolicy,
    private val prepare: HttpRequestBuilder.() -> Unit = {},
    private val onAnswer: (status: Int) -> Unit = {},
    private val onNoAnswer: () -> Unit = {},
) {
    /**
     * Sends the request and turns the outcome into an [ApiResult]; [decode] reads a success body (a failure to read it is
     * [ApiError.BadResponse]). With [retry] a failure that may pass ([isRetryable]) is tried again, up to the policy's attempts in all.
     */
    suspend fun <T> execute(
        decode: suspend (HttpResponse) -> T,
        retry: Boolean = false,
        configure: HttpRequestBuilder.() -> Unit,
    ): ApiResult<T> {
        var result = attempt(decode, configure)
        if (!retry) return result
        var wait = retries.backoff
        repeat(retries.attempts - 1) {
            if (!result.isRetryable()) return result
            delay(wait)
            wait *= 2
            result = attempt(decode, configure)
        }
        return result
    }

    private suspend fun <T> attempt(
        decode: suspend (HttpResponse) -> T,
        configure: HttpRequestBuilder.() -> Unit,
    ): ApiResult<T> {
        val response = try {
            http.request {
                // Not named `build`: inside this block that would call the builder's own build().
                configure()
                prepare()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            onNoAnswer()
            return ApiResult.Failure(ApiError.Network)
        }
        val status = response.status.value
        onAnswer(status)
        if (status !in 200..299) return ApiResult.Failure(errorFor(status))
        return try {
            ApiResult.Success(decode(response))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            ApiResult.Failure(ApiError.BadResponse)
        }
    }
}

/** What an unsuccessful HTTP [status] means. */
internal fun errorFor(status: Int): ApiError = when {
    status == 401 -> ApiError.Unauthorized
    status == 403 -> ApiError.Forbidden
    status == 404 -> ApiError.NotFound
    status >= 500 -> ApiError.Server(status)
    else -> ApiError.Unexpected(status)
}
