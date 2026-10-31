package app.vibeyfin.data.api

import io.ktor.http.HttpMethod

/**
 * `POST /QuickConnect/Authorize`: approves, as signed-in user [userId], a Quick Connect [code] another app started on this
 * server; Seerr's silent sign-in uses it. A code this server never issued is [ApiError.NotFound].
 */
suspend fun JellyfinApi.authorizeQuickConnect(code: String, userId: String): ApiResult<Boolean> = send(HttpMethod.Post, "/QuickConnect/Authorize", mapOf("code" to code, "userId" to userId))
