package com.liamgleeson.vibeyfin.di

import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

/**
 * Starts Koin once per process. Each platform entry point calls this; [platform] is for what
 * must be set before modules load (Android: `androidContext`). [platformModule] is added here.
 */
fun initKoin(platform: KoinAppDeclaration = {}): KoinApplication = startKoin {
    platform()
    modules(platformModule(), appModule())
}
