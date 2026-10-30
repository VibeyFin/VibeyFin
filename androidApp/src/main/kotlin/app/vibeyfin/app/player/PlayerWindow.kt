package app.vibeyfin.app.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.ui.PlayerView
import app.vibeyfin.R
import kotlin.concurrent.thread

/**
 * Full screen while the player is up: system bars hidden (a swipe from the edge brings them back for
 * a moment), the screen kept on (no touches arrive while watching, so it would dim and sleep otherwise;
 * Media3's PlayerView leaves that to the app) and landscape. [restore] puts all three back as
 * they were, which also leaves the user's rotation lock alone. It is called as the player starts to leave,
 * so the screen underneath is turning upright while the app's slide transition shows it, and again
 * (harmlessly) on the way out.
 */
internal class PlayerWindow(private val activity: Activity) {
    private val previousOrientation = activity.requestedOrientation
    private val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
    private var applied = false

    private val previousRotationAnimation = activity.window.attributes.rotationAnimation
    private val previousCutoutMode = activity.window.attributes.layoutInDisplayCutoutMode

    fun apply() {
        if (applied) return

        applied = true

        // For as long as the player is up, paused or not: the window flag only counts while the app is in front
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Turning the phone as the player opens or closes cross-fades instead of spinning a snapshot
        setRotationAnimation(WindowManager.LayoutParams.ROTATION_ANIMATION_CROSSFADE)

        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

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

    /**
     * Puts the bars and the orientation back. The orientation is a call into the system that can take
     * hundreds of milliseconds and makes everything lay out again, so it is made off the main thread,
     * and it is done once the screen is gone rather than while it slides away.
     */
    fun restore() {
        if (!applied) return

        applied = false

        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        controller.show(WindowInsetsCompat.Type.systemBars())
        thread(name = "restore-orientation") { activity.requestedOrientation = previousOrientation }
    }

    /** Back to the window's own rotation animation, once the screen has turned back. */
    fun release() {
        setRotationAnimation(previousRotationAnimation)

        activity.window.attributes = activity.window.attributes.apply { layoutInDisplayCutoutMode = previousCutoutMode }
    }

    private fun setRotationAnimation(animation: Int) {
        activity.window.attributes = activity.window.attributes.apply { rotationAnimation = animation }
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

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
