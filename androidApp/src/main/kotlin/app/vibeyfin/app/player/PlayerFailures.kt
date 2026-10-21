package app.vibeyfin.app.player

import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import app.vibeyfin.ui.playback.PlaybackFailure

/**
 * What Media3 says went wrong, as the shared ViewModel understands it. A lost connection, a timeout
 * and a server error (5xx) are [PlaybackFailure.Network]; a 401 is [PlaybackFailure.Unauthorized];
 * everything else, including every other HTTP status and any error Media3 doesn't name, is
 * [PlaybackFailure.Unsupported].
 */
@androidx.annotation.OptIn(UnstableApi::class)
fun PlaybackException.toFailure(): PlaybackFailure = when (errorCode) {
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
    PlaybackException.ERROR_CODE_TIMEOUT,
    -> PlaybackFailure.Network
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> when (httpStatus()) {
        401 -> PlaybackFailure.Unauthorized
        in 500..599 -> PlaybackFailure.Network
        else -> PlaybackFailure.Unsupported
    }
    else -> PlaybackFailure.Unsupported
}

/** The status the server answered with, found down the chain of causes (HLS wraps what its loaders throw). */
@androidx.annotation.OptIn(UnstableApi::class)
private fun PlaybackException.httpStatus(): Int? =
    generateSequence(cause) { it.cause }
        .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
        .firstOrNull()
        ?.responseCode
