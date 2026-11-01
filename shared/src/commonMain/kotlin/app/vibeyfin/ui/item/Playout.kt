package app.vibeyfin.ui.item

import app.vibeyfin.domain.PlayMethod
import app.vibeyfin.domain.PlaybackPlan
import app.vibeyfin.domain.toDiagnostics
import app.vibeyfin.ui.common.Strings

/** How the server would deliver a title to this device: the line under Play. [reason] is the first transcode reason, in plain words. */
data class Playout(val method: PlayMethod, val reason: String? = null) {
    /** What a screen reader says for the line. */
    val description: String get() = Strings.playoutDescription(method, reason)
}

/** The line for what the server offered. Only a transcode says why; a direct play or stream has nothing to explain. */
internal fun PlaybackPlan.toPlayout(): Playout {
    val reason = if (method == PlayMethod.Transcode) toDiagnostics(audioIndex = null).transcodeReasons.firstOrNull() else null
    return Playout(method, reason?.let(Strings::transcodeReason))
}
