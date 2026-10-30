package app.vibeyfin.data.api

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * How often a call that is safe to repeat is tried before its failure is returned. Only a server
 * that didn't answer ([ApiError.Network]) or answered with 5xx ([ApiError.Server]) is retried; a
 * 401, 403 or 404 would only come back the same. The wait doubles between tries.
 */
class RetryPolicy(val attempts: Int, val backoff: Duration) {
    init {
        require(attempts >= 1) { "attempts must be at least 1" }
    }

    companion object {
        val Default = RetryPolicy(attempts = 3, backoff = 400.milliseconds)

        val None = RetryPolicy(attempts = 1, backoff = Duration.ZERO)
    }
}

internal fun ApiResult<*>.isRetryable(): Boolean = this is ApiResult.Failure && (error is ApiError.Network || error is ApiError.Server)
