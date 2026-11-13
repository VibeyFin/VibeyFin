package app.vibeyfin.data.platform

/** What only the platform knows about this install. Bound per platform in `platformModule()`. */
interface DeviceInfo {
    /** The name the user gave the device, e.g. "Liam's Pixel"; falls back to the model. */
    val deviceName: String

    /** The marketing version of the app, e.g. "0.1.0". */
    val appVersion: String

    /** The device's language as an ISO 639-1 code, e.g. "de". */
    val language: String

    /** The device's full language tag, e.g. "pt-BR". */
    val languageTag: String get() = language

    /** False where subtitles are burned into the picture, so the caption style cannot apply. */
    val subtitleStyleApplies: Boolean get() = true

    /** True where the app notifies about downloads (iOS), so Settings offers the switch. */
    val downloadAlerts: Boolean get() = false

    val languagePickedInSystem: Boolean get() = false

    val debugBuild: Boolean get() = false
}
