package com.liamgleeson.vibeyfin.di

import com.liamgleeson.vibeyfin.ui.addserver.AddServerViewModel
import com.liamgleeson.vibeyfin.ui.servers.ServersViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Common bindings. [initKoin] adds [platformModule] next to it.
 *
 * A function, not a `val`: Koin keeps singletons inside the module object, so a shared module
 * would hand the same instances to every Koin application that loads it, tests included.
 */
fun appModule() = module {
    includes(dataModule(), apiModule())
    viewModelOf(::ServersViewModel)
    viewModel { params -> AddServerViewModel(params.getOrNull(), get(), get()) }
}
