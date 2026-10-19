package com.liamgleeson.vibeyfin.ios

import com.liamgleeson.vibeyfin.di.initKoin

/** Called once from the SwiftUI app's `init`. Named without an `init` prefix, which Obj-C export rewrites. */
fun startKoinIos() {
    initKoin()
}
