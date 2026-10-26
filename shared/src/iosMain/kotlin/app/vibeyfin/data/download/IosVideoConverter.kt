package app.vibeyfin.data.download

import app.vibeyfin.domain.VideoConversion
import app.vibeyfin.domain.VideoConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What the Swift side of the app does for a video: the remux itself is FFmpeg's, which is C and is called from Swift. Blocking, and
 * called off the main thread. Paths are absolute.
 */
interface NativeVideoConverter {
    /**
     * Converts [source] into [target] when AVPlayer cannot open it. [onProgress] gets 0 to 1 and answers whether to stop.
     * Returns [NOT_NEEDED], [CONVERTED], [UNSUPPORTED], [STOPPED] or [FAILED].
     */
    fun convert(source: String, target: String, onProgress: (Float) -> Boolean): Int

    fun canPlayVideo(codec: String): Boolean

    companion object {
        const val NOT_NEEDED = 0
        const val CONVERTED = 1
        const val UNSUPPORTED = 2
        const val STOPPED = 3
        const val FAILED = 4
    }
}

/** Where the Swift implementation is put when the app starts; [IosVideoConverter] is bound only once there is one. */
object NativeVideoConverterHost {
    var converter: NativeVideoConverter? = null
}

/** [VideoConverter] over the Swift side, which does the work on a background thread while this waits. */
class IosVideoConverter(private val files: IosDownloadFiles) : VideoConverter {
    private val native: NativeVideoConverter
        get() = checkNotNull(NativeVideoConverterHost.converter) { "The video converter was not registered at start." }

    override suspend fun convert(source: String, target: String, stop: StateFlow<Boolean>, onProgress: suspend (Float) -> Unit): VideoConversion =
        coroutineScope {
            val progress = MutableStateFlow(0f)
            val reporter = launch { progress.collect { onProgress(it) } }
            try {
                val code = withContext(Dispatchers.Default) {
                    native.convert(files.absolute(source), files.absolute(target)) { fraction ->
                        progress.value = fraction
                        stop.value
                    }
                }
                when (code) {
                    NativeVideoConverter.NOT_NEEDED -> VideoConversion.NotNeeded
                    NativeVideoConverter.CONVERTED -> VideoConversion.Converted
                    NativeVideoConverter.UNSUPPORTED -> VideoConversion.Unsupported
                    NativeVideoConverter.STOPPED -> VideoConversion.Stopped
                    else -> VideoConversion.Failed
                }
            } finally {
                reporter.cancel()
            }
        }

    override fun canPlayVideo(codec: String): Boolean = native.canPlayVideo(codec)
}
