package app.vibeyfin.data.download

import android.content.Context
import android.net.Uri
import app.vibeyfin.domain.DownloadFiles
import app.vibeyfin.domain.DownloadOutput
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * The downloads live in the app's private files folder (`files/downloads`), which no other app can read and which is not
 * in backups (backups are off for the app). Every path is checked to stay inside that folder.
 */
class AndroidDownloadFiles(context: Context) : DownloadFiles {
    private val root = File(context.filesDir, "downloads")

    private fun file(path: String): File {
        val file = File(root, path).canonicalFile
        require(file.path.startsWith(root.canonicalPath + File.separator)) { "Not a download path: $path" }
        return file
    }

    override fun length(path: String): Long = file(path).takeIf { it.isFile }?.length() ?: 0

    override fun exists(path: String): Boolean = file(path).exists()

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

    override fun delete(path: String) {
        file(path).deleteRecursively()
    }

    override fun freeBytes(): Long {
        root.mkdirs()
        return root.usableSpace
    }

    override fun uri(path: String): String = Uri.fromFile(file(path)).toString()
}
