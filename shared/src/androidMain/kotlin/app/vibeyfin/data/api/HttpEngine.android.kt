package app.vibeyfin.data.api

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.Dispatcher

// OkHttp runs at most 5 calls to one host at a time and queues the rest. Home starts about a dozen together (Continue watching,
// libraries, genres, My List, the hero, then a row each), so most of them waited a turn; a download in progress takes one too.
private const val MAX_REQUESTS_PER_HOST = 16

internal actual fun platformHttpEngine(): HttpClientEngine = OkHttp.create {
    config { dispatcher(Dispatcher().apply { maxRequestsPerHost = MAX_REQUESTS_PER_HOST }) }
}
