package app.vibeyfin.app.theme

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * False when the user turned animations off ("Remove animations", or the animator duration scale at 0), so
 * one-shot motion such as the artwork fade-in is skipped instead of played.
 */
@Composable
fun animationsEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
}
