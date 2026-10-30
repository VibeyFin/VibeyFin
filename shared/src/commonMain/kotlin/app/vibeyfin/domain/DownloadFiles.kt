package app.vibeyfin.domain

/**
 * Where downloaded files live, and the file work a download needs. Each platform keeps them where the user can find them
 * (iOS: Documents, shown in the Files app and left out of backups; Android: the app's own folder on shared storage, which Android deletes with the app). Paths are relative to the
 * downloads folder and use `/`: `{serverId}/{userId}/{itemId}/video.mkv`.
 */
interface DownloadFiles {
    /** The size of the file at [path]; 0 when there is none. */
    fun length(path: String): Long

    fun exists(path: String): Boolean

    /** Opens [path] for writing and makes its folders. With [append] what is there stays and writing goes on after it; otherwise the file starts empty. */
    fun open(path: String, append: Boolean): DownloadOutput

    /** Moves the file at [from] to [to], replacing what is there. */
    fun move(from: String, to: String)

    /** Deletes the file, or the folder with everything in it. Nothing happens when it isn't there. */
    fun delete(path: String)

    /** The bytes free on the storage the downloads are on. */
    fun freeBytes(): Long

    /** The `file://` address a player or an image loader opens [path] by. */
    fun uri(path: String): String
}

/** A file being written. */
interface DownloadOutput {
    fun write(bytes: ByteArray, count: Int)

    fun close()
}

/** What a platform without downloads yet binds: every call says so. */
object NoDownloadFiles : DownloadFiles {
    private fun unsupported(): Nothing = throw UnsupportedOperationException("Downloads are not available on this platform yet.")

    override fun length(path: String) = 0L

    override fun exists(path: String) = false

    override fun open(path: String, append: Boolean): DownloadOutput = unsupported()

    override fun move(from: String, to: String) = unsupported()

    override fun delete(path: String) = Unit

    override fun freeBytes() = 0L

    override fun uri(path: String): String = unsupported()
}
