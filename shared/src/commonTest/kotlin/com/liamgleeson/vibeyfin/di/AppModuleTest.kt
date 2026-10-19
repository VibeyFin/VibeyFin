package com.liamgleeson.vibeyfin.di

import com.liamgleeson.vibeyfin.ui.placeholder.PlaceholderUiState
import com.liamgleeson.vibeyfin.ui.placeholder.PlaceholderViewModel
import org.koin.dsl.koinApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class AppModuleTest {
    @Test
    fun appModuleResolvesEveryViewModel() {
        val koin = koinApplication { modules(appModule) }.koin

        val viewModel = koin.get<PlaceholderViewModel>()

        assertEquals(PlaceholderUiState(), viewModel.uiState.value)
    }
}
