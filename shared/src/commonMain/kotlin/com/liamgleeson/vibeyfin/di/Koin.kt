package com.liamgleeson.vibeyfin.di

import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

/**
 * Starts Koin once per process. Each platform entry point calls this and supplies its own
 * bindings through [platform] (Android: `androidContext` + platform modules; iOS: platform modules).
 */
fun initKoin(platform: KoinAppDeclaration = {}): KoinApplication = startKoin {
    platform()
    modules(appModule)
}
