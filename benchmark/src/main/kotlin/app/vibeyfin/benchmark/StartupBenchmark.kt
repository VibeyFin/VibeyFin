package app.vibeyfin.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * How quickly the app starts and how smoothly Home scrolls, with no profile and with the baseline profile, so the difference is
 * measured and a null result is a result. Run with `./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest` (the app's `benchmark`
 * build type), signed in on the local test server.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun coldStartup() = startup(CompilationMode.None())

    @Test
    fun coldStartupWithProfile() = startup(CompilationMode.Partial())

    @Test
    fun scrollHome() = scroll(CompilationMode.None())

    @Test
    fun scrollHomeWithProfile() = scroll(CompilationMode.Partial())

    private fun startup(mode: CompilationMode) = rule.measureRepeated(
        packageName = APP_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        iterations = 10,
        startupMode = StartupMode.COLD,
    ) {
        pressHome()
        startActivityAndWait()
        waitForHome()
    }

    private fun scroll(mode: CompilationMode) = rule.measureRepeated(
        packageName = APP_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = mode,
        iterations = 5,
        startupMode = StartupMode.WARM,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            waitForHome()
        },
    ) {
        scrollHome()
    }
}
