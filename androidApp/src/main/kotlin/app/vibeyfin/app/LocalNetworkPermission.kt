package app.vibeyfin.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

private const val FIRST_SDK_WITH_THE_PERMISSION = 37

/**
 * Holds back [content] until local network access has been answered for. The first connection starts with [content], and one
 * that starts before the answer fails and stays failed after Allow.
 */
@Composable
fun LocalNetworkPermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var asking by remember { mutableStateOf(needsLocalNetworkPermission(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { asking = false }

    LaunchedEffect(Unit) {
        if (asking) launcher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
    }

    if (!asking) content()
}

private fun needsLocalNetworkPermission(context: Context): Boolean = Build.VERSION.SDK_INT >= FIRST_SDK_WITH_THE_PERMISSION &&
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED
