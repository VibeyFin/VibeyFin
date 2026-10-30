package app.vibeyfin.app.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.media3.common.Format
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import app.vibeyfin.domain.LiveDiagnostics

/** Collects the player's measurements and hands them on when they change. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class StatsFeed(
    private val player: ExoPlayer,
    private val onChanged: (LiveDiagnostics) -> Unit,
    private val enabled: () -> Boolean,
) : AnalyticsListener {
    private var droppedFrames = 0
    private var bandwidthBps = 0L
    private var last: LiveDiagnostics? = null

    /** A new stream starts its own count. */
    fun reset() {
        droppedFrames = 0

        publish()
    }

    fun publish() {
        if (!enabled()) return

        val size = player.videoSize
        val now = liveDiagnostics(
            video = player.videoFormat,
            audio = player.audioFormat,
            width = size.width,
            height = size.height,
            droppedFrames = droppedFrames,
            bandwidthBps = bandwidthBps,
            bufferMs = player.totalBufferedDuration,
        )

        if (now == last) return

        last = now

        onChanged(now)
    }

    override fun onVideoInputFormatChanged(eventTime: AnalyticsListener.EventTime, format: Format, decoderReuseEvaluation: DecoderReuseEvaluation?) = publish()

    override fun onAudioInputFormatChanged(eventTime: AnalyticsListener.EventTime, format: Format, decoderReuseEvaluation: DecoderReuseEvaluation?) = publish()

    override fun onVideoSizeChanged(eventTime: AnalyticsListener.EventTime, videoSize: VideoSize) = publish()

    override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
        this.droppedFrames += droppedFrames

        publish()
    }

    override fun onBandwidthEstimate(eventTime: AnalyticsListener.EventTime, totalLoadTimeMs: Int, totalBytesLoaded: Long, bitrateEstimate: Long) {
        bandwidthBps = bitrateEstimate

        publish()
    }
}
