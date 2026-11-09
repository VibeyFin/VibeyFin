package app.vibeyfin.domain

import app.vibeyfin.data.api.MediaSourceDto

internal data class SourceChoice(val source: MediaSourceDto, val method: PlayMethod)

/** Direct play, then the first server stream; null when the server offers neither. */
internal fun chooseSource(sources: List<MediaSourceDto>, forceTranscode: Boolean): SourceChoice? {
    val directPlay: MediaSourceDto? = sources.firstOrNull { it.supportsDirectPlay && !forceTranscode }
    if (directPlay != null) return SourceChoice(directPlay, PlayMethod.DirectPlay)

    val transcodeSource: MediaSourceDto = sources.firstOrNull { it.transcodingUrl != null } ?: return null
    val method: PlayMethod = if (transcodeSource.supportsDirectStream && !forceTranscode) PlayMethod.DirectStream else PlayMethod.Transcode
    return SourceChoice(transcodeSource, method)
}
