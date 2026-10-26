package app.vibeyfin.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Walks what a person does first: start, scroll Home, open an item. Run with `./gradlew :androidApp:generateBaselineProfile`; the
 * profile lands in `androidApp/src/main/generated/baselineProfiles` and is committed. The app must be signed in on the local test
 * server (`docs/test-server.md`).
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = APP_PACKAGE) {
        pressHome()
        startActivityAndWait()
        waitForHome()
        scrollHome()
        openAnItem()
    }
}
