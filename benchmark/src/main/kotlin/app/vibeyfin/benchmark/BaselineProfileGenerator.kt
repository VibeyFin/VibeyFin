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
    // Also the startup profile, which R8 uses to put what a cold start needs together in the APK
    fun generate() = rule.collect(packageName = APP_PACKAGE, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        waitForHome()
        scrollHome()
        openAnItem()
    }
}
