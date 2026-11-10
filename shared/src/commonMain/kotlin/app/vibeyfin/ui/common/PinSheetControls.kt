package app.vibeyfin.ui.common

/** What a screen's PIN and password sheets send to its ViewModel. */
interface PinSheetControls {
    /** A PIN was entered in the open PIN sheet. */
    fun onSubmitPin(pin: String)

    /** "Forgot PIN?": the PIN sheet asks for the profile's Jellyfin password instead. */
    fun onForgotPin()

    fun onDismissPin()

    /** The Jellyfin password entered after "Forgot PIN?". */
    fun onSubmitPassword(password: String)
}
