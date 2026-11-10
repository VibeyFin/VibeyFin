package app.vibeyfin.ui.downloads

/** What the "Remove downloads?" confirmation sends to its ViewModel. */
interface RemovalControls {
    fun onConfirmRemove()

    fun onDismissRemoval()
}
