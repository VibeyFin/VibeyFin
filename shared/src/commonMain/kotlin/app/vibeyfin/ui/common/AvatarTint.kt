package app.vibeyfin.ui.common

object AvatarTint {
    const val COUNT = 6

    fun of(name: String): Int {
        // The JVM's string hash, written out so every platform gets the same number
        val hash = name.lowercase().fold(0) { acc, char -> 31 * acc + char.code }
        return (hash and Int.MAX_VALUE) % COUNT
    }
}
