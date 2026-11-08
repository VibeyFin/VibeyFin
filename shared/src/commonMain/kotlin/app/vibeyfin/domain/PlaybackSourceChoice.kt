package app.vibeyfin.domain

import app.vibeyfin.data.api.MediaSourceDto

internal class SourceChoice(val source: MediaSourceDto, val method: PlayMethod)

/** Direct play, then the first server stream; null when the server offers neither. */
internal fun chooseSource(sources: List<MediaSourceDto>, forceTranscode: Boolean): SourceChoice? {
    val directPlay: MediaSourceDto? = sources.firstOrNull { it.supportsDirectPlay && !forceTranscode }
    if (directPlay != null) return SourceChoice(directPlay, PlayMethod.DirectPlay)

    val served: MediaSourceDto = sources.firstOrNull { it.transcodingUrl != null } ?: return null
    val method: PlayMethod = if (served.supportsDirectStream && !forceTranscode) PlayMethod.DirectStream else PlayMethod.Transcode
    return SourceChoice(served, method)
}
