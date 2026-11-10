package app.vibeyfin.ios

import app.vibeyfin.ui.downloads.RemovalControls

/** The "Remove downloads?" methods of a screen's facade, forwarded once to [removal]. */
interface RemovalModel {
    val removal: RemovalControls

    fun onConfirmRemove() = removal.onConfirmRemove()

    fun onDismissRemoval() = removal.onDismissRemoval()
}
