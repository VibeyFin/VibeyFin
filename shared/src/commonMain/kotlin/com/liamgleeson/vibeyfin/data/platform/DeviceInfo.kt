package com.liamgleeson.vibeyfin.data.platform

/** What only the platform knows about this install. Bound per platform in `platformModule()`. */
interface DeviceInfo {
    /** The name the user gave the device, e.g. "Liam's Pixel"; falls back to the model. */
    val deviceName: String

    /** The marketing version of the app, e.g. "0.1.0". */
    val appVersion: String
}
