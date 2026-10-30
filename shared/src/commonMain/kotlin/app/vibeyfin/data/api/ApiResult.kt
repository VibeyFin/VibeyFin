package app.vibeyfin.data.api

/** What a call to the server came back with, so callers branch on it instead of catching exceptions. */
sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>

    data class Failure(val error: ApiError) : ApiResult<Nothing>
}

sealed interface ApiError {
    /** HTTP 401: the credentials or the token were rejected. */
    data object Unauthorized : ApiError

    /** HTTP 403. */
    data object Forbidden : ApiError

    /** HTTP 404. */
    data object NotFound : ApiError

    /** HTTP 5xx, typically a server starting up or a proxy that can't reach it. */
    data class Server(val status: Int) : ApiError

    /** Any other status that isn't a success. */
    data class Unexpected(val status: Int) : ApiError

    /** Nothing answered: wrong address, no network, a timeout, or a failed TLS handshake. */
    data object Network : ApiError

    /** The server answered with success but the body wasn't what the call needs. */
    data object BadResponse : ApiError
}

fun <T> ApiResult<T>.valueOrNull(): T? = (this as? ApiResult.Success)?.value

fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(value))
    is ApiResult.Failure -> this
}
