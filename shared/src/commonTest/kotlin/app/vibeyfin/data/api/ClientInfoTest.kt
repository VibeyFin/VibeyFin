package app.vibeyfin.data.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClientInfoTest {
    private val info = ClientInfo(deviceName = "Pixel 8", deviceId = "abc-123", appVersion = "0.1.0")

    @Test
    fun theHeaderHasTheMediaBrowserShapeWithoutAToken() {
        assertEquals(
            "MediaBrowser Client=\"VibeyFin\", Device=\"Pixel 8\", DeviceId=\"abc-123\", Version=\"0.1.0\"",
            info.authorizationHeader(),
        )
    }

    @Test
    fun theTokenIsAddedOnceSignedIn() {
        assertEquals(
            "MediaBrowser Client=\"VibeyFin\", Device=\"Pixel 8\", DeviceId=\"abc-123\", Version=\"0.1.0\", Token=\"deadbeef\"",
            info.authorizationHeader("deadbeef"),
        )
    }

    @Test
    fun valuesAreEncodedSoTheServerCanDecodeThem() {
        val header = info.copy(deviceName = "Liam’s \"Pixel\", 100%+").authorizationHeader()

        // Quote, comma, percent and plus would break parsing or decode to something else.
        assertTrue(header.contains("Device=\"Liam%E2%80%99s %22Pixel%22%2C 100%25%2B\""), header)
    }

    @Test
    fun emojiAreEncodedAsUtf8Bytes() {
        val header = info.copy(deviceName = "📱").authorizationHeader()

        assertTrue(header.contains("Device=\"%F0%9F%93%B1\""), header)
    }

    @Test
    fun theHeaderIsAlwaysPlainAscii() {
        val header = info.copy(deviceName = "Täst ☃ 手機", appVersion = "1.0-β").authorizationHeader("tök")

        assertTrue(header.all { it.code in 32..126 }, header)
        assertFalse(header.contains('€'))
    }
}
