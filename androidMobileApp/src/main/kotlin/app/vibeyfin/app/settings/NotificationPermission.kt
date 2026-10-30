package app.vibeyfin.app.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Asks for the notification permission when Settings [requested] it (Notifications was switched on). The request is
 * acknowledged at once; a refusal calls [onDenied]. Below Android 13, or with the permission already granted, there is
 * nothing to ask.
 */
@Composable
fun NotificationPermissionRequest(requested: Boolean, onRequested: () -> Unit, onDenied: () -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) onDenied()
    }

    LaunchedEffect(requested) {
        if (!requested) return@LaunchedEffect

        onRequested()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
