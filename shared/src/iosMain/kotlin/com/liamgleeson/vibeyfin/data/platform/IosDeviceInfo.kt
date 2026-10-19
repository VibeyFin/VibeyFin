package com.liamgleeson.vibeyfin.data.platform

import platform.Foundation.NSBundle
import platform.UIKit.UIDevice

class IosDeviceInfo : DeviceInfo {
    // Since iOS 16 the name is a generic "iPhone" unless the app has the device-name entitlement.
    override val deviceName: String = UIDevice.currentDevice.name

    override val appVersion: String =
        NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "0"
}
