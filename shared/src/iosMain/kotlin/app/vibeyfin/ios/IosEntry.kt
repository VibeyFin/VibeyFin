package app.vibeyfin.ios

import app.vibeyfin.di.initKoin

/** Called once from the SwiftUI app's `init`. Named without an `init` prefix, which Obj-C export rewrites. */
fun startKoinIos() {
    initKoin()
}
