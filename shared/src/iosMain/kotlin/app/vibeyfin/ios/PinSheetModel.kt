package app.vibeyfin.ios

import app.vibeyfin.ui.common.PinSheetControls

/** The PIN and password sheet methods of a screen's facade, forwarded once to [pinSheet]. */
interface PinSheetModel {
    val pinSheet: PinSheetControls

    fun onSubmitPin(pin: String) = pinSheet.onSubmitPin(pin)

    fun onForgotPin() = pinSheet.onForgotPin()

    fun onDismissPin() = pinSheet.onDismissPin()

    fun onSubmitPassword(password: String) = pinSheet.onSubmitPassword(password)
}
