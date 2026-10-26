package app.vibeyfin.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

const val APP_PACKAGE = "app.vibeyfin"

/**
 * The app must already be signed in on the local test server (`docs/test-server.md`): the journeys start from Home and do not
 * type a password. Passes once the Home tab is on screen.
 */
fun MacrobenchmarkScope.waitForHome() {
    device.wait(Until.hasObject(By.text("Home")), 15_000)
}

/** Scrolls the main list down and back up a few times, which is what the first-scroll profile is for. */
fun MacrobenchmarkScope.scrollHome() {
    val width = device.displayWidth
    val height = device.displayHeight
    repeat(3) {
        device.swipe(width / 2, height * 3 / 4, width / 2, height / 4, 30)
        device.waitForIdle()
    }
    repeat(3) {
        device.swipe(width / 2, height / 4, width / 2, height * 3 / 4, 30)
        device.waitForIdle()
    }
}

/** Opens the first poster in the first row below the fold and goes back. */
fun MacrobenchmarkScope.openAnItem() {
    device.findObject(By.scrollable(true))?.scroll(Direction.DOWN, 0.5f)
    device.waitForIdle()
    val poster = device.findObject(By.clickable(true).hasDescendant(By.desc("poster")))
        ?: device.findObject(By.clickable(true).res(APP_PACKAGE, "poster"))
    poster?.click()
    device.waitForIdle()
    device.pressBack()
    device.waitForIdle()
}
