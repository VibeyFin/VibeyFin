package app.vibeyfin.i18n

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSBundle
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile

actual fun platformStringSource(): StringSource = BundleStringSource

private const val TEST_DIRECTORY_VARIABLE: String = "VIBEYFIN_STRINGS_DIR"

@OptIn(ExperimentalForeignApi::class)
private object BundleStringSource : StringSource {
    override fun load(tag: String): String? {
        val bundled: String? = NSBundle.mainBundle.pathForResource("strings/$tag", "json")
        // Unit tests run outside the app bundle; Gradle points them at shared/strings
        val testDirectory: String? = NSProcessInfo.processInfo.environment[TEST_DIRECTORY_VARIABLE] as? String
        val path: String = bundled ?: testDirectory?.let { "$it/$tag.json" } ?: return null
        return NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null)
    }
}
