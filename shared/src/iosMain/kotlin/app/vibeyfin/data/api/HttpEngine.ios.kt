package app.vibeyfin.data.api

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin

// The Darwin engine as Ktor would pick it by itself; URLSession's own limits apply.
internal actual fun platformHttpEngine(): HttpClientEngine = Darwin.create()
