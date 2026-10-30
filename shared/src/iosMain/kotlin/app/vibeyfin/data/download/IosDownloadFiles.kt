package app.vibeyfin.data.download

import app.vibeyfin.domain.DownloadFiles
import app.vibeyfin.domain.DownloadOutput
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSFileHandle
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSFileSystemFreeSize
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.closeFile
import platform.Foundation.fileHandleForWritingAtPath
import platform.Foundation.create
import platform.Foundation.seekToEndOfFile
import platform.Foundation.truncateFileAtOffset
import platform.Foundation.writeData

/**
 * The downloads in the app's Documents folder, so the user finds them in the Files app, left out of iCloud and device backups: a movie
 * that can be downloaded again is not worth the user's iCloud space. Paths are relative to [root], so the app's container moving (it
 * does on an update) loses nothing; [uri] is worked out when asked. [legacyRoot] is where earlier versions kept them (Application
 * Support): what is left there moves to [root] when this is made.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosDownloadFiles(private val root: String, legacyRoot: String? = null) : DownloadFiles {
    private val manager = NSFileManager.defaultManager

    init {
        manager.createDirectoryAtPath(root, withIntermediateDirectories = true, attributes = null, error = null)
        NSURL.fileURLWithPath(root).setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = null)
        legacyRoot?.let(::adopt)
    }

    // TODO: remove the migration (this, `legacyRoot` and its call in PlatformModule.ios.kt) once nobody has downloads in Application Support: the app is in beta and only the developers do.
    /** Moves each server's folder from [legacyRoot] into [root], on the same volume so it costs nothing, then removes the old folder. */
    private fun adopt(legacyRoot: String) {
        if (!manager.fileExistsAtPath(legacyRoot)) return
        manager.contentsOfDirectoryAtPath(legacyRoot, error = null)?.filterIsInstance<String>()?.forEach { name ->
            val target = "$root/$name"
            // Never over what is there already: a folder that is in both places stays where it is and is dropped from the old one.
            if (!manager.fileExistsAtPath(target)) manager.moveItemAtPath("$legacyRoot/$name", toPath = target, error = null)
        }
        manager.removeItemAtPath(legacyRoot, error = null)
    }

    /** The full path of a relative one. */
    fun absolute(path: String): String {
        require(!path.split('/').contains("..")) { "Not a download path: $path" }
        return "$root/$path"
    }

    override fun length(path: String): Long {
        val attributes = manager.attributesOfItemAtPath(absolute(path), error = null) ?: return 0
        return (attributes[NSFileSize] as? NSNumber)?.longLongValue ?: 0
    }

    override fun exists(path: String): Boolean = manager.fileExistsAtPath(absolute(path))

    override fun open(path: String, append: Boolean): DownloadOutput {
        val full = absolute(path)
        manager.createDirectoryAtPath(full.substringBeforeLast('/'), withIntermediateDirectories = true, attributes = null, error = null)
        if (!manager.fileExistsAtPath(full)) manager.createFileAtPath(full, contents = null, attributes = null)
        val handle = checkNotNull(NSFileHandle.fileHandleForWritingAtPath(full)) { "Cannot write $path" }
        if (append) handle.seekToEndOfFile() else handle.truncateFileAtOffset(0u)
        return object : DownloadOutput {
            override fun write(bytes: ByteArray, count: Int) {
                if (count == 0) return
                bytes.usePinned { pinned -> handle.writeData(NSData.create(bytes = pinned.addressOf(0), length = count.toULong())) }
            }

            override fun close() = handle.closeFile()
        }
    }

    override fun move(from: String, to: String) {
        val target = absolute(to)
        manager.createDirectoryAtPath(target.substringBeforeLast('/'), withIntermediateDirectories = true, attributes = null, error = null)
        manager.removeItemAtPath(target, error = null)
        check(manager.moveItemAtPath(absolute(from), toPath = target, error = null)) { "Cannot move $from" }
    }

    override fun delete(path: String) {
        manager.removeItemAtPath(absolute(path), error = null)
    }

    override fun freeBytes(): Long =
        (manager.attributesOfFileSystemForPath(root, error = null)?.get(NSFileSystemFreeSize) as? NSNumber)?.longLongValue ?: 0

    override fun uri(path: String): String = NSURL.fileURLWithPath(absolute(path)).absoluteString ?: "file://${absolute(path)}"
}
