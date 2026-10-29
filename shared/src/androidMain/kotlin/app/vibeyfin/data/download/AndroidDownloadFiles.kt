package app.vibeyfin.data.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import app.vibeyfin.domain.DownloadFiles
import app.vibeyfin.domain.DownloadOutput
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * The downloads live in the app's private files folder (`files/downloads`), which no other app can read and which is not
 * in backups (backups are off for the app). Every path is checked to stay inside that folder.
 *
 * A finished video is [publish]ed: copied into `Movies/VibeyFin` through MediaStore, where the user finds it in any file manager,
 * and the private copy goes. A `.published` file next to where the video was holds its `content://` address, and that is how every
 * call on the video's path then reaches it. Before Android 10 there is no such folder without a storage permission, so the video
 * stays private.
 */
class AndroidDownloadFiles(context: Context) : DownloadFiles {
    private val root = File(context.filesDir, "downloads")
    private val resolver = context.contentResolver

    private fun file(path: String): File {
        val file = File(root, path).canonicalFile
        require(file.path.startsWith(root.canonicalPath + File.separator)) { "Not a download path: $path" }
        return file
    }

    private fun marker(file: File) = File(file.path + PUBLISHED)

    /** Where the video at [path] was published, or null when it is not (or no longer) a published one. */
    private fun published(path: String): Uri? = marker(file(path)).takeIf { it.isFile }?.readText()?.let(Uri::parse)

    override fun length(path: String): Long {
        published(path)?.let { uri -> return runCatching { resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } }.getOrNull()?.coerceAtLeast(0) ?: 0 }
        return file(path).takeIf { it.isFile }?.length() ?: 0
    }

    override fun exists(path: String): Boolean {
        published(path)?.let { uri -> return runCatching { resolver.openAssetFileDescriptor(uri, "r")?.use { true } }.getOrNull() ?: false }
        return file(path).exists()
    }

    override fun open(path: String, append: Boolean): DownloadOutput {
        val target = file(path)
        target.parentFile?.mkdirs()
        val stream = FileOutputStream(target, append)
        return object : DownloadOutput {
            override fun write(bytes: ByteArray, count: Int) = stream.write(bytes, 0, count)

            override fun close() = stream.close()
        }
    }

    override fun move(from: String, to: String) {
        val target = file(to)
        target.parentFile?.mkdirs()
        Files.move(file(from).toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    override fun publish(path: String, displayName: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val source = file(path)
        if (!source.isFile) return
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, mimeType(displayName))
            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/$PUBLIC_FOLDER")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val target = runCatching { resolver.insert(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values) }.getOrNull() ?: return
        // Copying needs room for the video a second time until the private one is deleted; when it fails the video simply stays private.
        val copied = runCatching {
            resolver.openOutputStream(target)!!.use { out -> source.inputStream().use { it.copyTo(out) } }
            resolver.update(target, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
        }.isSuccess
        if (!copied) {
            runCatching { resolver.delete(target, null, null) }
            return
        }
        marker(source).writeText(target.toString())
        source.delete()
    }

    override fun delete(path: String) {
        val target = file(path)
        if (target.isDirectory) target.walkTopDown().filter { it.name.endsWith(PUBLISHED) }.toList().forEach(::unpublish) else unpublish(marker(target))
        target.deleteRecursively()
    }

    /** Takes the video a [marker] points at out of the Movies folder, and the marker with it. */
    private fun unpublish(marker: File) {
        if (!marker.isFile) return
        runCatching { resolver.delete(Uri.parse(marker.readText()), null, null) }
        marker.delete()
    }

    override fun freeBytes(): Long {
        root.mkdirs()
        return root.usableSpace
    }

    override fun uri(path: String): String = published(path)?.toString() ?: Uri.fromFile(file(path)).toString()

    private fun mimeType(name: String): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.').lowercase())?.takeIf { it.startsWith("video/") }
            ?: if (name.endsWith(".mkv", ignoreCase = true)) "video/x-matroska" else "video/mp4"

    private companion object {
        const val PUBLISHED = ".published"
        const val PUBLIC_FOLDER = "VibeyFin"
    }
}
