package app.vibeyfin.data.platform

import platform.Foundation.NSBundle
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.languageCode
import platform.Foundation.preferredLanguages
import platform.UIKit.UIDevice

class IosDeviceInfo : DeviceInfo {
    // Since iOS 16 the name is a generic "iPhone" unless the app has the device-name entitlement.
    override val deviceName: String = UIDevice.currentDevice.name

    // AVPlayer gets every subtitle burned in (no text formats are advertised), so the style cannot apply.
    override val subtitleStyleApplies: Boolean = false

    override val downloadAlerts: Boolean = true

    override val languagePickedInSystem: Boolean = true

    override val language: String = NSLocale.currentLocale.languageCode

    override val languageTag: String get() = (NSLocale.preferredLanguages.firstOrNull() as? String) ?: language

    override val appVersion: String =
        NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "0"
}
