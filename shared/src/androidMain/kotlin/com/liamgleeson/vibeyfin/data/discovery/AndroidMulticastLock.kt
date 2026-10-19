package com.liamgleeson.vibeyfin.data.discovery

import android.content.Context
import android.net.wifi.WifiManager

/**
 * Holds a [WifiManager.MulticastLock] so the Wi-Fi chip passes broadcast packets up while a
 * search runs. Needs `CHANGE_WIFI_MULTICAST_STATE`.
 */
class AndroidMulticastLock(context: Context) : MulticastLock {
    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    override suspend fun <T> withLock(block: suspend () -> T): T {
        val lock = wifi.createMulticastLock(TAG).apply {
            setReferenceCounted(false)
            acquire()
        }
        try {
            return block()
        } finally {
            if (lock.isHeld) lock.release()
        }
    }

    private companion object {
        const val TAG = "VibeyFin discovery"
    }
}
