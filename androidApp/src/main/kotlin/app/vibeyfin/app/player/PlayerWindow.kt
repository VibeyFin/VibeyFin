package app.vibeyfin.app.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.Build
import android.provider.Settings
import android.view.OrientationEventListener
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

private const val SIDEWAYS_FROM = 60
private const val SIDEWAYS_TO = 120
private const val UPRIGHT_WITHIN = 30
private const val HALF_TURN = 180

/**
 * Full screen while the player is up: system bars hidden (a swipe from the edge brings them back for
 * a moment), the screen kept on (no touches arrive while watching, so it would dim and sleep otherwise;
 * Media3's PlayerView leaves that to the app) and landscape. [PlayerTurnSequence] says
 * when the screen may change around a turn. [restore] puts all three back as they were, which also leaves the user's rotation lock alone.
 */
internal class PlayerWindow(private val activity: Activity) {
    private val previousOrientation = activity.requestedOrientation
    private val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
    private var applied = false

    private val previousCutoutMode = activity.window.attributes.layoutInDisplayCutoutMode

    // An orientation request can take hundreds of milliseconds, and a quick back press must not overtake the one before it
    private val orientationRequests = Executors.newSingleThreadExecutor()
    private val orientationDispatcher = orientationRequests.asCoroutineDispatcher()

    private var heldSideways: Boolean? = null
    private val sensor = object : OrientationEventListener(activity) {
        override fun onOrientationChanged(degrees: Int) {
            heldSideways = heldSideways(degrees)
        }
    }

    private val openedUpright = !screenFacts().landscape

    val opensWithTurn = opensWithTurn(screenFacts())

    fun apply() {
        if (applied) return

        applied = true

        // For as long as the player is up, paused or not: the window flag only counts while the app is in front
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        sensor.enable()

        requestLandscape()

        // The picture runs into the camera cut-out too (landscape on a phone, and on a foldable's cover screen): left alone the
        // window keeps a bar of its own color beside it.
        activity.window.attributes = activity.window.attributes.apply {
            layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    fun keepScreenOn(on: Boolean) {
        if (!applied) return

        if (on) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    fun closesWithTurn(): Boolean {
        val locked = Settings.System.getInt(activity.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 0

        return closesWithTurn(screenFacts(), locked, openedUpright, heldSideways)
    }

    private fun requestLandscape() {
        orientationRequests.execute { activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE }
    }

    suspend fun turnBack() = withContext(orientationDispatcher) { activity.requestedOrientation = previousOrientation }

    private fun restoreOrientation() {
        orientationRequests.execute { activity.requestedOrientation = previousOrientation }
    }

    fun restore() {
        if (!applied) return

        applied = false

        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        controller.show(WindowInsetsCompat.Type.systemBars())
        restoreOrientation()
    }

    fun release() {
        sensor.disable()
        orientationRequests.shutdown()

        activity.window.attributes = activity.window.attributes.apply { layoutInDisplayCutoutMode = previousCutoutMode }
    }

    private fun screenFacts() = activity.playerScreenFacts(activity.resources.configuration)

    private fun heldSideways(degrees: Int): Boolean? {
        if (degrees == OrientationEventListener.ORIENTATION_UNKNOWN) return null

        val fromUpright = degrees % HALF_TURN

        return when {
            fromUpright in SIDEWAYS_FROM..SIDEWAYS_TO -> true
            fromUpright < UPRIGHT_WITHIN || fromUpright > HALF_TURN - UPRIGHT_WITHIN -> false
            else -> null
        }
    }
}

@Composable
internal fun rememberPlayerWindow(): PlayerWindow? {
    val context = LocalContext.current
    val window = remember(context) { context.findActivity()?.let(::PlayerWindow) }

    DisposableEffect(window) {
        window?.apply()
        onDispose {
            window?.restore()
            window?.release()
        }
    }

    return window
}

// The system's own turn moves the screen, so the app's transition would only fight it
class PlayerTurns internal constructor(private val activity: Activity?) {
    val opening get() = activity != null && opensWithTurn(activity.playerScreenFacts(activity.resources.configuration))

    // The player asks for the orientation back before it leaves only when that turns the screen
    val leaving get() = activity != null &&
        activity.playerScreenFacts(activity.resources.configuration).obeysOrientation &&
        activity.requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
}

@Composable
fun rememberPlayerTurns(): PlayerTurns {
    val context = LocalContext.current

    return remember(context) { PlayerTurns(context.findActivity()) }
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
