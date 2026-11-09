package app.vibeyfin.app.theme

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * False when the user turned animations off ("Remove animations", or the animator duration scale at 0), so
 * motion such as the artwork fade-in or the skeleton shimmer is skipped instead of played. Follows the setting while the app runs.
 */
@Composable
fun animationsEnabled(): Boolean {
    val resolver: ContentResolver = LocalContext.current.contentResolver
    var enabled: Boolean by remember(resolver) { mutableStateOf(readAnimationsEnabled(resolver)) }

    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                enabled = readAnimationsEnabled(resolver)
            }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }

    return enabled
}

private fun readAnimationsEnabled(resolver: ContentResolver): Boolean = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
