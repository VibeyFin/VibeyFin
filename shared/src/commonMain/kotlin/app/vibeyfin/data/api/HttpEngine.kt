package app.vibeyfin.data.api

import io.ktor.client.engine.HttpClientEngine

/**
 * The engine the clients that talk to servers run on: OkHttp on Android, Darwin on iOS. A screen such as Home asks one
 * server for many lists at once, so the platform sets how many may be in flight to it together.
 */
internal expect fun platformHttpEngine(): HttpClientEngine
