package app.vibeyfin.app.player

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.domain.PlaybackDiagnostics
import app.vibeyfin.domain.TrickplayPreviewSize
import app.vibeyfin.domain.TrickplayTrack
import app.vibeyfin.ui.common.Strings
import coil3.SingletonImageLoader
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import java.util.concurrent.ConcurrentHashMap

/**
 * What the stream is and how it is arriving, in a compact panel on the scrim so it reads over any
 * video. It only draws: it takes no touches, so taps still reach the player underneath.
 */
@Composable
fun StatsOverlay(diagnostics: PlaybackDiagnostics, modifier: Modifier = Modifier) {
    val none = Strings.statsUnavailable
    val rows = buildList {
        add(Strings.statsLabelMethod to (diagnostics.method?.let(Strings::statsMethod) ?: none))

        if (diagnostics.transcodeReasons.isNotEmpty()) {
            add(Strings.statsLabelReasons to diagnostics.transcodeReasons.joinToString("\n"))
        }

        add(Strings.statsLabelVideo to (diagnostics.videoCodec ?: none))
        add(Strings.statsLabelAudio to (diagnostics.audioCodec ?: none))
        add(
            Strings.statsLabelResolution to
                if (diagnostics.width != null && diagnostics.height != null) {
                    Strings.statsResolution(diagnostics.width!!, diagnostics.height!!)
                } else {
                    none
                },
        )
        add(Strings.statsLabelFrameRate to (diagnostics.frameRate?.let(Strings::statsFrameRate) ?: none))
        add(Strings.statsLabelBitrate to (diagnostics.bitrateKbps?.let(Strings::statsBitrate) ?: none))
        add(Strings.statsLabelDroppedFrames to (diagnostics.droppedFrames?.toString() ?: none))
        add(Strings.statsLabelThroughput to (diagnostics.throughputKbps?.let(Strings::statsBitrate) ?: none))
        add(Strings.statsLabelBuffer to (diagnostics.bufferMs?.let(Strings::statsBuffer) ?: none))
    }

    Column(
        modifier = modifier
            .widthIn(max = 260.dp)
            .background(VibeyFinTokens.Colors.scrim, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        rows.forEach { (label, value) ->
            // One spoken phrase per row, "Bitrate, 4.5 Mbps", instead of two separate texts
            Row(
                Modifier.clearAndSetSemantics { contentDescription = Strings.labelled(label, value) },
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = VibeyFinTokens.Colors.inkMuted,
                    modifier = Modifier.widthIn(min = 88.dp),
                )
                Text(text = value, style = MaterialTheme.typography.labelSmall, color = VibeyFinTokens.Colors.ink)
            }
        }
    }
}

/**
 * "Skip intro", "Skip credits". Shown for the whole segment whether or not the controls are up.
 * The label comes from the shared state.
 */
@Composable
fun SkipButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(VibeyFinTokens.Size.touch),
        colors = ButtonDefaults.filledTonalButtonColors(contentColor = VibeyFinTokens.Colors.ink),
    ) {
        Text(label)
    }
}

/**
 * "Are you still watching?" over the paused video, after several episodes in a row that started by
 * themselves. It covers the picture and takes every touch, so nothing underneath is pressed by accident.
 */
@Composable
fun StillWatchingPrompt(onContinue: () -> Unit, onStop: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(VibeyFinTokens.Colors.scrim)
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s4)) {
            Text(
                text = Strings.stillWatchingPrompt,
                style = MaterialTheme.typography.headlineSmall,
                color = VibeyFinTokens.Colors.ink,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3)) {
                FilledTonalButton(onClick = onStop, modifier = Modifier.height(VibeyFinTokens.Size.touch)) {
                    Text(Strings.stillWatchingStop)
                }

                Button(onClick = onContinue, modifier = Modifier.height(VibeyFinTokens.Size.touch)) {
                    Text(Strings.stillWatchingContinue)
                }
            }
        }
    }
}

/**
 * The scrub-preview thumbnails of one playback: fetches the tile images with the stream's headers,
 * keeps them decoded, and crops one thumbnail out by the shared maths. Make a new one for a new
 * track (another episode); dropping it from composition drops the cache. A tile that fails to load
 * counts as missing for a short while (scrubbing carries on with the timestamp alone), then it is tried again.
 */
class TrickplayTiles(private val context: Context, private val track: TrickplayTrack, private val headers: Map<String, String>) {
    // Bounded by bytes; a tile that falls out is fetched again when it is needed
    private val tiles = object : LruCache<Int, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount
    }

    private val failed = ConcurrentHashMap<Int, Long>()

    /** How big the preview is drawn over a bar [barWidth] dp wide on a screen of [screenScale] pixels to the dp. */
    fun previewSize(barWidth: Float, screenScale: Float): TrickplayPreviewSize = track.previewSize(barWidth, screenScale)

    /** Which tile image holds the thumbnail for [positionMs]. */
    fun tileAt(positionMs: Long): Int = track.locate(positionMs).tileIndex

    /** The thumbnail for [positionMs], or null while its tile is loading and when it can't be had. */
    suspend fun thumbnail(positionMs: Long): ImageBitmap? {
        val at = track.locate(positionMs)
        val tile = tiles.get(at.tileIndex) ?: load(at.tileIndex) ?: return null
        val x = at.column * track.thumbWidth
        val y = at.row * track.thumbHeight

        if (x + track.thumbWidth > tile.width || y + track.thumbHeight > tile.height) return null
        return Bitmap.createBitmap(tile, x, y, track.thumbWidth, track.thumbHeight).asImageBitmap()
    }

    private companion object {
        const val CACHE_BYTES = 32 * 1024 * 1024
    }

    private suspend fun load(index: Int): Bitmap? {
        val failedAt = failed[index]

        if (failedAt != null) {
            if (!track.mayRetryTile(failedAt, SystemClock.elapsedRealtime())) return null

            failed.remove(index)
        }

        val request = ImageRequest.Builder(context)
            .data(track.tileUrl(index))
            .httpHeaders(NetworkHeaders.Builder().apply { headers.forEach { (name, value) -> set(name, value) } }.build())
            // Cropping needs pixels in memory; the tiles are held here, not in Coil's shared caches
            .allowHardware(false)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .build()
        val result = SingletonImageLoader.get(context).execute(request)
        val bitmap = (result as? SuccessResult)?.image?.toBitmap()

        if (bitmap == null) failed[index] = SystemClock.elapsedRealtime() else tiles.put(index, bitmap)
        return bitmap
    }
}

/**
 * The thumbnail above the scrubber's thumb with the time under it, as the design draws it: no box,
 * the picture at [size] with the pop shadow and a hairline. [fraction] is where the thumb is along the
 * bar; the preview is centered on it but kept inside the bar's width, so it never leaves the screen at
 * either end. It takes no touches.
 */
@Composable
fun ScrubPreview(thumbnail: ImageBitmap, time: String, fraction: Float, size: TrickplayPreviewSize, modifier: Modifier = Modifier) {
    val pictureShape: RoundedCornerShape = RoundedCornerShape(VibeyFinTokens.Radius.artwork)

    Column(
        modifier = modifier.layout { measurable, constraints ->
            // The slider's height must not clamp the picture
            val placeable = measurable.measure(Constraints())
            // Sits on top of the bar without taking room in it
            layout(0, 0) {
                val centred = (constraints.maxWidth * fraction - placeable.width / 2f).toInt()
                placeable.place(centred.coerceIn(0, (constraints.maxWidth - placeable.width).coerceAtLeast(0)), -placeable.height)
            }
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            bitmap = thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size.width.dp, size.height.dp)
                .shadow(PreviewShadowElevation, pictureShape, ambientColor = VibeyFinTokens.Colors.scrimStrong, spotColor = VibeyFinTokens.Colors.scrimStrong)
                .clip(pictureShape)
                .border(1.dp, VibeyFinTokens.Colors.overlayWeak, pictureShape),
        )
        Text(
            text = time,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFeatureSettings = "tnum",
                shadow = Shadow(VibeyFinTokens.Colors.scrimStrong, blurRadius = TIME_SHADOW_BLUR),
            ),
            color = VibeyFinTokens.Colors.ink,
            modifier = Modifier.padding(top = VibeyFinTokens.Space.s1),
        )
    }
}

private val PreviewShadowElevation = 12.dp
private const val TIME_SHADOW_BLUR: Float = 2f
