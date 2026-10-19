package com.liamgleeson.vibeyfin.ui.startup

import com.liamgleeson.vibeyfin.domain.StartDestination
import com.liamgleeson.vibeyfin.testing.TestApp
import com.liamgleeson.vibeyfin.testing.ViewModelTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class StartupViewModelTest {
    private val app = TestApp()
    private val viewModels = ViewModelTracker()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() = runTest {
        viewModels.stopAll()
        Dispatchers.resetMain()
        app.close()
    }

    @Test
    fun reportsWhereTheAppOpens() = runTest {
        app.serverAt("http://nas:8096")

        val viewModel = viewModels.track(StartupViewModel(app.router))

        assertEquals(StartDestination.WhoIsWatching("srv-1"), viewModel.destination.first { it != null })
    }

    @Test
    fun aFreshInstallOpensOnServers() = runTest {
        val viewModel = viewModels.track(StartupViewModel(app.router))

        assertEquals(StartDestination.Servers, viewModel.destination.first { it != null })
    }
}
