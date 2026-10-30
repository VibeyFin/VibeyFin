package app.vibeyfin.ios

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelHostTest {
    private class FakeViewModel : ViewModel()

    private val state = MutableStateFlow(0)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        startKoin { }
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
        Dispatchers.resetMain()
    }

    private fun host() = ViewModelHost(FakeViewModel::class) { FakeViewModel() }

    @Test
    fun observeReportsTheCurrentStateThenEveryChange() {
        val seen = mutableListOf<Int>()
        host().observe(state) { seen += it }

        state.value = 1
        state.value = 2

        assertEquals(listOf(0, 1, 2), seen)
    }

    @Test
    fun observingAgainReplacesThePreviousObserver() {
        val host = host()
        val first = mutableListOf<Int>()
        val second = mutableListOf<Int>()
        host.observe(state) { first += it }
        host.observe(state) { second += it }

        state.value = 1

        assertEquals(listOf(0), first)
        assertEquals(listOf(0, 1), second)
    }

    @Test
    fun closeStopsReportingChanges() {
        val host = host()
        val seen = mutableListOf<Int>()
        host.observe(state) { seen += it }

        host.close()
        state.value = 1

        assertEquals(listOf(0), seen)
    }
}
