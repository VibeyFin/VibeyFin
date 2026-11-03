package app.vibeyfin.ui.common

/**
 * Which of the six avatar tints (`avatar-1` to `avatar-6`) a profile without a picture sits on: stable per name and the same on both
 * platforms, so its tile, the profile button and the Profile page all show one colour.
 */
object AvatarTint {
    const val COUNT = 6

    fun of(name: String): Int {
        // The JVM's string hash, written out so every platform gets the same number
        val hash = name.lowercase().fold(0) { acc, char -> 31 * acc + char.code }
        return (hash and Int.MAX_VALUE) % COUNT
    }
}
