package app.vibeyfin.data.api

import app.vibeyfin.data.platform.DeviceInfo
import app.vibeyfin.domain.DevicePreferences

/** Who is calling, as shown in the Jellyfin dashboard's device list. */
data class ClientInfo(
    val deviceName: String,
    val deviceId: String,
    val appVersion: String,
    val clientName: String = CLIENT_NAME,
) {
    companion object {
        const val CLIENT_NAME = "VibeyFin"
    }
}

/** Assembles [ClientInfo] when a request needs it; the device ID comes from storage, so it suspends. */
class ClientInfoProvider(
    private val deviceInfo: DeviceInfo,
    private val preferences: DevicePreferences,
) {
    suspend fun current() = ClientInfo(
        deviceName = deviceInfo.deviceName,
        deviceId = preferences.deviceId(),
        appVersion = deviceInfo.appVersion,
    )
}

/**
 * The `Authorization` header Jellyfin expects on every request. [token] is only present once
 * signed in.
 *
 * Jellyfin runs each value through `WebUtility.UrlDecode`, so values are percent-encoded. That
 * keeps device names like "Liam’s Phone" valid in an HTTP header, and keeps a comma or a quote in
 * a name from breaking the header apart.
 */
fun ClientInfo.authorizationHeader(token: String? = null): String = buildString {
    append("MediaBrowser Client=\"").append(clientName.headerEncoded())
    append("\", Device=\"").append(deviceName.headerEncoded())
    append("\", DeviceId=\"").append(deviceId.headerEncoded())
    append("\", Version=\"").append(appVersion.headerEncoded())
    append('"')
    if (token != null) append(", Token=\"").append(token.headerEncoded()).append('"')
}

private const val HEX = "0123456789ABCDEF"

private fun String.headerEncoded(): String = buildString {
    for (byte in this@headerEncoded.encodeToByteArray()) {
        val value = byte.toInt() and 0xFF
        if (value.isSafeInHeader()) {
            append(value.toChar())
        } else {
            append('%').append(HEX[value shr 4]).append(HEX[value and 0x0F])
        }
    }
}

// Letters, digits, space and `-_.~` pass through; everything else is encoded byte by byte.
private fun Int.isSafeInHeader(): Boolean = this in 'a'.code..'z'.code ||
    this in 'A'.code..'Z'.code ||
    this in '0'.code..'9'.code ||
    this == ' '.code || this == '-'.code || this == '_'.code || this == '.'.code || this == '~'.code
