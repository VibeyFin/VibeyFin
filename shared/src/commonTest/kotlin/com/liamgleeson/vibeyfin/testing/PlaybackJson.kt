package com.liamgleeson.vibeyfin.testing

/**
 * One media source in a `PlaybackInfo` answer. By default the server says the device can direct play
 * it; pass [directPlay] = false and a [transcodingUrl] for a remux or a transcode.
 */
fun mediaSourceJson(
    id: String = "ms-1",
    container: String? = "mp4",
    directPlay: Boolean = true,
    directStream: Boolean = true,
    transcodingUrl: String? = null,
    runtimeTicks: Long? = 90 * TICKS_PER_MINUTE,
    etag: String? = "etag-1",
    /** Audio and subtitle streams (see [audioStreamJson] and [subtitleStreamJson]); the video is always index 0. */
    streams: List<String> = emptyList(),
    /** What the server says is selected: the user's choice if the request named this source, else its own pick. */
    defaultAudio: Int? = null,
    defaultSubtitle: Int? = null,
    /** The video stream's picture: size and the server's `VideoRangeType` (`SDR`, `HDR10`, `DOVIWithHDR10`, `HLG`, ...); null leaves it out. */
    videoWidth: Int? = null,
    videoHeight: Int? = null,
    videoRangeType: String? = null,
): String {
    fun string(value: String?) = value?.let { "\"$it\"" } ?: "null"
    val video = listOfNotNull(
        """"Index":0,"Type":"Video","Codec":"h264"""",
        videoWidth?.let { """"Width":$it""" },
        videoHeight?.let { """"Height":$it""" },
        videoRangeType?.let { """"VideoRangeType":"$it"""" },
    ).joinToString(",", "{", "}")
    val allStreams = listOf(video) + streams
    return """{"Id":"$id","Container":${string(container)},"ETag":${string(etag)},"RunTimeTicks":${runtimeTicks ?: "null"},""" +
        """"SupportsDirectPlay":$directPlay,"SupportsDirectStream":$directStream,"SupportsTranscoding":${transcodingUrl != null},""" +
        """"TranscodingUrl":${string(transcodingUrl)},"TranscodingSubProtocol":${if (transcodingUrl != null) "\"hls\"" else "null"},""" +
        """"DefaultAudioStreamIndex":${defaultAudio ?: "null"},"DefaultSubtitleStreamIndex":${defaultSubtitle ?: "null"},""" +
        """"MediaStreams":[${allStreams.joinToString(",")}],"SomethingWeDontRead":true}"""
}

/** An audio stream in a media source. [displayTitle] is the server's name for it. */
fun audioStreamJson(index: Int, displayTitle: String? = null, language: String? = null, codec: String = "aac"): String {
    fun string(value: String?) = value?.let { "\"$it\"" } ?: "null"
    return """{"Index":$index,"Type":"Audio","Codec":"$codec","Language":${string(language)},"DisplayTitle":${string(displayTitle)}}"""
}

/**
 * A subtitle stream. [delivery] is what the server would do if it were shown: `External` (a file at
 * [url]) or `Encode` (burned into the video, no file).
 */
fun subtitleStreamJson(
    index: Int,
    displayTitle: String? = null,
    language: String? = null,
    delivery: String = "External",
    url: String? = "/Videos/m1/ms-1/Subtitles/$index/0/Stream.srt?ApiKey=server-key",
    codec: String = "subrip",
): String {
    fun string(value: String?) = value?.let { "\"$it\"" } ?: "null"
    return """{"Index":$index,"Type":"Subtitle","Codec":"$codec","Language":${string(language)},"DisplayTitle":${string(displayTitle)},""" +
        """"DeliveryMethod":"$delivery","DeliveryUrl":${if (delivery == "External") string(url) else "null"}}"""
}

/** A `PlaybackInfo` answer. [playSessionId] null leaves it out, like a server that sent nothing usable. */
fun playbackInfoJson(vararg sources: String, playSessionId: String? = "ps-1", errorCode: String? = null): String {
    val fields = listOfNotNull(
        """"MediaSources":[${sources.joinToString(",")}]""",
        playSessionId?.let { """"PlaySessionId":"$it"""" },
        errorCode?.let { """"ErrorCode":"$it"""" },
    )
    return fields.joinToString(prefix = "{", postfix = "}")
}

/** The path the server gives for an HLS transcode, as 10.10 does: relative, with its own key. */
const val HLS_PATH = "/videos/m1/master.m3u8?MediaSourceId=ms-1&PlaySessionId=ps-1&ApiKey=server-key"

/** A `/MediaSegments/{id}` answer. */
fun segmentsJson(vararg segments: String) =
    """{"Items":[${segments.joinToString(",")}],"TotalRecordCount":${segments.size},"StartIndex":0}"""

/** One marked segment of "m1", from [startMs] to [endMs]. */
fun segmentJson(type: String, startMs: Long, endMs: Long) =
    """{"Id":"s-$type","ItemId":"m1","Type":"$type","StartTicks":${startMs * 10_000},"EndTicks":${endMs * 10_000}}"""
