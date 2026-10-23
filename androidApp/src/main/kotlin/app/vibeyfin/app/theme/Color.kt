package app.vibeyfin.app.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The `android` column of design/tokens.json. DesignTokensTest fails when a value drifts from it, so
 * change both together. Screens read these through MaterialTheme where a role exists, and from here
 * for the rest (scrims, subtle ink, progress track, focus).
 */
object VibeyFinTokens {
    object Colors {
        val bg = Color(0xFF0E1514)
        val surface1 = Color(0xFF161D1C)
        val surface2 = Color(0xFF1A2120)
        val surface3 = Color(0xFF242B2A)
        val line = Color(0xFF3B4A47)
        val lineStrong = Color(0xFF859491)
        val ink = Color(0xFFDDE4E2)
        val inkMuted = Color(0xFFBACAC6)
        val inkSubtle = Color(0xFF859491)

        /** Electric aqua. The one filled primary action per screen, and playback progress. */
        val accent = Color(0xFF3FE0D0)
        val accentPressed = Color(0xFF2BBFB1)
        val onAccent = Color(0xFF003732)
        val accentSoft = Color(0xFF194F49)
        val onAccentSoft = Color(0xFF8BBFB7)
        val danger = Color(0xFFFFB4AB)

        /** Under any text or control laid over artwork or video. */
        val scrim = Color(0xC7000000)
        val scrimStrong = Color(0xE6000000)
        val progressTrack = Color(0x4DDDE4E2)
        val focus = Color(0xFFDDE4E2)

        /** Profile tile grounds, picked from the user's name; the initial sits on one in [ink]. */
        val avatar1 = Color(0xFF3A4A6B)
        val avatar2 = Color(0xFF4B3F6B)
        val avatar3 = Color(0xFF3F6B5E)
        val avatar4 = Color(0xFF6B4B3F)
        val avatar5 = Color(0xFF6B3F55)
        val avatar6 = Color(0xFF55633F)
        val avatars = listOf(avatar1, avatar2, avatar3, avatar4, avatar5, avatar6)
    }

    object Space {
        val s1 = 4.dp
        val s2 = 8.dp
        val s3 = 12.dp
        val s4 = 16.dp
        val s5 = 24.dp
        val s6 = 32.dp
        val s7 = 48.dp
    }

    object Radius {
        /** Posters, thumbnails and the hero card. */
        val artwork = 8.dp
        val m3Xs = 4.dp
        val m3Md = 12.dp
        val m3Xl = 28.dp
    }

    object Size {
        val touch = 48.dp
        val button = 40.dp
        val buttonLarge = 56.dp
        val posterWidth = 112.dp
        val thumbWidth = 224.dp
    }

    object Motion {
        const val baseMs = 140
        const val heroMs = 600
    }
}
