package com.liamgleeson.vibeyfin.data.api

import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType

/**
 * `POST /Items/{id}/PlaybackInfo`: asks how this device can play the item. The server answers with
 * its choice per media source: direct play, direct stream or a transcode. Not retried: it opens a
 * play session on the server.
 */
suspend fun JellyfinApi.playbackInfo(itemId: String, request: PlaybackInfoRequestDto): ApiResult<PlaybackInfoResponseDto> =
    post("/Items/$itemId/PlaybackInfo", request, mapOf("userId" to request.userId))

/** `POST /Sessions/Playing`: playback began (or began again after a stop). */
suspend fun JellyfinApi.reportPlaybackStart(info: PlaybackStartDto): ApiResult<Unit> =
    postWithoutAnswer("/Sessions/Playing", info)

/** `POST /Sessions/Playing/Progress`: where playback is, and whether it is paused. */
suspend fun JellyfinApi.reportPlaybackProgress(info: PlaybackProgressDto): ApiResult<Unit> =
    postWithoutAnswer("/Sessions/Playing/Progress", info)

/** `POST /Sessions/Playing/Stopped`: playback ended, at the position in [info]. */
suspend fun JellyfinApi.reportPlaybackStopped(info: PlaybackStopDto): ApiResult<Unit> =
    postWithoutAnswer("/Sessions/Playing/Stopped", info)

/** The reports answer 204 with no body; nothing is read from them. */
private suspend inline fun <reified B : Any> JellyfinApi.postWithoutAnswer(path: String, body: B): ApiResult<Unit> =
    execute({ }) {
        method = HttpMethod.Post
        url("$baseUrl$path")
        contentType(ContentType.Application.Json)
        setBody(body)
    }
