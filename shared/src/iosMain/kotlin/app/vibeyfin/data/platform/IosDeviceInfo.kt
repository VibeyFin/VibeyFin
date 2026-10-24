package app.vibeyfin.data.platform

import platform.Foundation.NSBundle
import platform.UIKit.UIDevice

class IosDeviceInfo : DeviceInfo {
    // Since iOS 16 the name is a generic "iPhone" unless the app has the device-name entitlement.
    override val deviceName: String = UIDevice.currentDevice.name

    // AVPlayer gets every subtitle burned in (no text formats are advertised), so the style cannot apply.
    override val subtitleStyleApplies: Boolean = false

    override val appVersion: String =
        NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "0"
}
