package app.vibeyfin.app.player

import android.app.Activity
import android.content.res.Configuration
import android.os.Build

private const val BAKLAVA = 36
private const val LARGE_SCREEN_DP = 600

internal data class PlayerScreenFacts(val landscape: Boolean, val smallestWidthDp: Int, val sdk: Int, val multiWindow: Boolean) {
    // Android 16 ignores an app's orientation on large screens, as a window sharing the screen always has
    val obeysOrientation get() = !multiWindow && (sdk < BAKLAVA || smallestWidthDp < LARGE_SCREEN_DP)
}

internal fun opensWithTurn(screen: PlayerScreenFacts) = screen.obeysOrientation && !screen.landscape

// A null heldSideways is a phone lying flat or tilted, where the system keeps the rotation it has
internal fun closesWithTurn(screen: PlayerScreenFacts, rotationLocked: Boolean, openedUpright: Boolean, heldSideways: Boolean?) = when {
    !screen.obeysOrientation || !screen.landscape -> false
    rotationLocked -> openedUpright
    else -> heldSideways == false
}

internal fun Activity.playerScreenFacts(configuration: Configuration) = PlayerScreenFacts(
    landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
    smallestWidthDp = configuration.smallestScreenWidthDp,
    sdk = Build.VERSION.SDK_INT,
    multiWindow = isInMultiWindowMode,
)
