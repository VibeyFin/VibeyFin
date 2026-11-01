package app.vibeyfin.ios

import app.vibeyfin.domain.DevicePreferences
import app.vibeyfin.domain.PlaybackEngineChoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import org.koin.mp.KoinPlatform

/**
 * The device's playback engine choice, readable synchronously from Swift (`PlaybackEngineChoiceStore.shared.current`).
 * Reads the stored choice on first use, then follows it.
 * PoC: remove with poc/playback-engine
 */
object PlaybackEngineChoiceStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var choice: PlaybackEngineChoice = PlaybackEngineChoice.Auto

    /** The choice the next video should play with. */
    val current: PlaybackEngineChoice get() = choice

    init {
        val preferences = KoinPlatform.getKoin().get<DevicePreferences>()
        // The saved choice is read before the first answer, or the first video after a launch would always use Native.
        choice = runBlocking { preferences.playbackEngine.first() }
        preferences.playbackEngine
            .onEach { choice = it }
            .launchIn(scope)
    }
}
