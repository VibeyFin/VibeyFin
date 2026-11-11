package app.vibeyfin.app.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp

internal var activeThemeColors: ThemeColors by mutableStateOf(ThemePalettes.byId(null))

/** What screens read: the active theme's colours, and the `android` values of the other token groups. */
object VibeyFinTokens {
    val Colors: ThemeColors get() = activeThemeColors

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
        const val BASE_MS = 140
        const val HERO_MS = 600
    }
}
