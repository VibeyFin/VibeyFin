package app.vibeyfin.app.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.ExoPlayer
import app.vibeyfin.domain.SubtitleFile
import app.vibeyfin.ui.playback.PlaybackStream
import app.vibeyfin.ui.playback.SUBTITLES_OFF
import app.vibeyfin.ui.playback.TrackOption

private fun subtitleId(index: Int) = "vibeyfin-subtitle-$index"

/**
 * The stream as a Media3 item. Every subtitle the server sends as a file goes in with the video and
 * none is shown yet: [applyTrackSelection] picks one, and switching later needs no new item.
 */
fun PlaybackStream.toMediaItem(title: String? = null, subtitle: String? = null, artworkUrl: String? = null): MediaItem = MediaItem.Builder()
    .setUri(url)
    .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setSubtitle(subtitle).setArtworkUri(artworkUrl?.let(Uri::parse)).build())
    .setSubtitleConfigurations(subtitleFiles.map { it.toConfiguration() })
    .build()

private fun SubtitleFile.toConfiguration(): MediaItem.SubtitleConfiguration = MediaItem.SubtitleConfiguration.Builder(Uri.parse(url))
    .setId(subtitleId(index))
    .setMimeType(mimeType)
    .setLanguage(language)
    .setLabel(label)
    .setSelectionFlags(0)
    .build()

/**
 * Makes the player show what the shared state says is selected, as far as the player can do it:
 *  - the selected subtitle, if the server sent it as a file (otherwise subtitles are off here: they
 *    are either not wanted or already part of the picture);
 *  - the selected audio track, for a direct play, by its position among the file's audio tracks.
 *    If the player lists a different number of audio tracks than the server does, it is left alone
 *    rather than guessed at.
 *
 * Safe to call again whenever the selection or the player's tracks change: it only sets what differs.
 */
fun ExoPlayer.applyTrackSelection(stream: PlaybackStream, audio: List<TrackOption>, subtitles: List<TrackOption>) {
    val groups = currentTracks.groups
    val builder = trackSelectionParameters.buildUpon()

    val subtitleIndex = subtitles.firstOrNull { it.selected }?.index?.takeIf { it != SUBTITLES_OFF }
    val subtitleGroup = subtitleIndex?.let { index ->
        groups.firstOrNull { group ->
            group.type == C.TRACK_TYPE_TEXT && group.mediaTrackGroup.getFormat(0).id?.endsWith(subtitleId(index)) == true
        }
    }

    if (subtitleGroup != null) {
        // An override does not undo "disabled", which is how subtitles are switched off: say both
        builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(TrackSelectionOverride(subtitleGroup.mediaTrackGroup, 0))
    } else {
        builder.clearOverridesOfType(C.TRACK_TYPE_TEXT).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
    }

    if (stream.audioInPlayer && audio.size > 1) {
        val audioGroups = groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        val position = audio.indexOfFirst { it.selected }

        if (audioGroups.size == audio.size && position >= 0) {
            builder.setOverrideForType(TrackSelectionOverride(audioGroups[position].mediaTrackGroup, 0))
        }
    }

    trackSelectionParameters = builder.build()
}
