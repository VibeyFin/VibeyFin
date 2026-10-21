package com.liamgleeson.vibeyfin.app.player

import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle

/** Whether the player is in a picture-in-picture window now. */
@Stable
class PlayerPip internal constructor(
    /** The device can show a picture-in-picture window, so the player offers a button for it. */
    val available: Boolean,
) {
    var active by mutableStateOf(false)
        internal set

    internal var enterAction: () -> Unit = {}

    /** Turns the screen into the window now, as leaving the app would. */
    fun enter() = enterAction()
}

/** The widest and narrowest windows Android accepts. */
private const val MIN_RATIO = 0.42f
private const val MAX_RATIO = 2.39f

private fun pipParams(aspectRatio: Float, autoEnter: Boolean): PictureInPictureParams {
    val ratio = aspectRatio.coerceIn(MIN_RATIO, MAX_RATIO)
    return PictureInPictureParams.Builder()
        .setAspectRatio(Rational((ratio * 1000).toInt(), 1000))
        .apply { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setAutoEnterEnabled(autoEnter) }
        .build()
}

/**
 * Picture-in-picture for the player: while [enabled] (something is playing), leaving the app turns the
 * screen into a window that keeps playing. From Android 12 the system does it by itself; before that the
 * activity is asked as the user leaves. [onEntered] and [onLeft] say what happened; [onLeft] says whether
 * the window was dismissed (closed) or expanded back to the app (not closed).
 */
@Composable
fun rememberPlayerPip(
    enabled: Boolean,
    aspectRatio: Float,
    onEntered: () -> Unit,
    onLeft: (closed: Boolean) -> Unit,
): PlayerPip {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() as? ComponentActivity }
    val available = activity?.packageManager?.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) == true
    val pip = remember(available) { PlayerPip(available) }
    val canEnter = enabled && available
    val currentEnabled by rememberUpdatedState(canEnter)
    val currentRatio by rememberUpdatedState(aspectRatio)
    val entered by rememberUpdatedState(onEntered)
    val left by rememberUpdatedState(onLeft)

    pip.enterAction = { activity?.enterPictureInPictureMode(pipParams(currentRatio, autoEnter = false)) }

    LaunchedEffect(canEnter, aspectRatio) {
        if (available) activity?.setPictureInPictureParams(pipParams(aspectRatio, autoEnter = canEnter))
    }
    DisposableEffect(activity, available) {
        if (activity == null || !available) return@DisposableEffect onDispose {}
        val modeChanged = Consumer<PictureInPictureModeChangedInfo> { info ->
            pip.active = info.isInPictureInPictureMode
            if (info.isInPictureInPictureMode) {
                entered()
            } else {
                // A dismissed window leaves the activity stopped; an expanded one brings it back to the screen.
                left(!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
            }
        }
        // Before Android 12 there is no automatic entry.
        val userLeaves = Runnable {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && currentEnabled) {
                activity.enterPictureInPictureMode(pipParams(currentRatio, autoEnter = false))
            }
        }
        activity.addOnPictureInPictureModeChangedListener(modeChanged)
        activity.addOnUserLeaveHintListener(userLeaves)
        onDispose {
            activity.removeOnPictureInPictureModeChangedListener(modeChanged)
            activity.removeOnUserLeaveHintListener(userLeaves)
            // Other screens must not turn into a window.
            activity.setPictureInPictureParams(pipParams(currentRatio, autoEnter = false))
        }
    }
    return pip
}
