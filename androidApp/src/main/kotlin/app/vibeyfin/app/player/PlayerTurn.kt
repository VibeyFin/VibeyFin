package app.vibeyfin.app.player

private const val QUARTER_TURN = 90
private const val FULL_TURN = 360
private const val HALF_TURN = 180

internal data class PlayerTurn(val degrees: Float, val scale: Float)

/** [width] and [height] are the window's after the turn, which swaps them. */
internal fun turnFrom(oldRotation: Int, newRotation: Int, width: Float, height: Float, pictureRatio: Float): PlayerTurn {
    val turn = (oldRotation - newRotation) * QUARTER_TURN
    val degrees = ((turn + FULL_TURN + HALF_TURN - 1) % FULL_TURN) - HALF_TURN + 1

    return PlayerTurn(degrees.toFloat(), fittedWidth(height, width, pictureRatio) / fittedWidth(width, height, pictureRatio))
}

private fun fittedWidth(width: Float, height: Float, pictureRatio: Float) = minOf(width, height * pictureRatio)

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
