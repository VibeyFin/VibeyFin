package com.liamgleeson.vibeyfin

import androidx.compose.ui.window.ComposeUIViewController
import com.liamgleeson.vibeyfin.di.initKoin
import platform.UIKit.UIViewController

/** Called once from `iOSApp.init`. Named without an `init` prefix, which Obj-C export rewrites. */
fun startKoinIos() {
    initKoin()
}

fun MainViewController(): UIViewController = ComposeUIViewController { App() }
