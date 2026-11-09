package app.vibeyfin.data.platform

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import java.util.Locale

class AndroidDeviceInfo(private val context: Context) : DeviceInfo {
    override val deviceName: String by lazy {
        Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
            ?.takeIf { it.isNotBlank() }
            ?: Build.MODEL
    }

    override val language: String get() = Locale.getDefault().toLanguageTag().substringBefore('-')

    override val appVersion: String by lazy {
        val packages = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packages.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packages.getPackageInfo(context.packageName, 0)
        }
        info.versionName ?: "0"
    }
}
