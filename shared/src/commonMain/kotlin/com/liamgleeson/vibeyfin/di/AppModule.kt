package com.liamgleeson.vibeyfin.di

import com.liamgleeson.vibeyfin.ui.placeholder.PlaceholderViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Common bindings. Platform bindings (token vault, device name, …) are passed to [initKoin]. */
val appModule = module {
    viewModelOf(::PlaceholderViewModel)
}
