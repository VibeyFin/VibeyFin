package app.vibeyfin.data.platform

/** What only the platform knows about this install. Bound per platform in `platformModule()`. */
interface DeviceInfo {
    /** The name the user gave the device, e.g. "Liam's Pixel"; falls back to the model. */
    val deviceName: String

    /** The marketing version of the app, e.g. "0.1.0". */
    val appVersion: String

    /** False where subtitles are burned into the picture, so the caption style cannot apply. */
    val subtitleStyleApplies: Boolean get() = true

    /** False where the player has no swipe for volume and brightness (iOS keeps the system controls), so its setting is hidden. */
    val swipeGesturesApply: Boolean get() = true
}
