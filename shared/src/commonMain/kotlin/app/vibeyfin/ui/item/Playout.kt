package app.vibeyfin.ui.item

import app.vibeyfin.domain.PlayMethod
import app.vibeyfin.domain.PlaybackPlan
import app.vibeyfin.domain.toDiagnostics
import app.vibeyfin.ui.common.Strings

/** How the server would deliver one stream to this device, said after the codec in the technical info. [reason] is the first transcode reason, in plain words. */
data class Playout(val method: PlayMethod, val reason: String? = null)

/** The tags for a title. Audio the device can't play tags the audio track [audioTrack] (its place in the file's audio tracks) instead of the video, which the server leaves alone. */
data class PlayoutTags(val video: Playout? = null, val audio: Playout? = null, val audioTrack: Int? = null)

/** The tags for what the server offered. Only a transcode says why; a direct play or stream has nothing to explain. */
internal fun PlaybackPlan.toPlayoutTags(): PlayoutTags {
    if (method != PlayMethod.Transcode) return PlayoutTags(video = Playout(method))
    val (audio, other) = toDiagnostics(audioIndex = null).transcodeReasons.partition { it.startsWith("Audio") || it == "SecondaryAudioNotSupported" }
    return PlayoutTags(
        video = if (other.isNotEmpty() || audio.isEmpty()) Playout(method, other.firstOrNull()?.let(Strings::transcodeReason)) else null,
        audio = audio.firstOrNull()?.let { Playout(method, Strings.transcodeReason(it)) },
        audioTrack = audioTracks.indexOfFirst { it.index == audioStreamIndex }.takeIf { it >= 0 },
    )
}
