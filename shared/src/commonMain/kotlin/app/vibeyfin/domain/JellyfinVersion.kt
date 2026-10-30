package app.vibeyfin.domain

data class JellyfinVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<JellyfinVersion> {
    override fun compareTo(other: JellyfinVersion): Int = compareValuesBy(this, other, JellyfinVersion::major, JellyfinVersion::minor, JellyfinVersion::patch)

    override fun toString() = "$major.$minor.$patch"

    companion object {
        /** The oldest server we support. */
        val MINIMUM = JellyfinVersion(10, 10, 0)

        /**
         * Reads "10.10.7", "12.2.0", "10.8.13.0" (a fourth part is ignored) or "10.11.0-rc1"
         * (the suffix is ignored). A missing patch counts as 0. Returns null for anything else.
         */
        fun parse(text: String): JellyfinVersion? {
            val numeric = text.trim().takeWhile { it.isDigit() || it == '.' }
            val parts = numeric.split('.')
            if (parts.size < 2) return null
            val numbers = parts.take(3).map { it.toIntOrNull() ?: return null }
            return JellyfinVersion(numbers[0], numbers[1], numbers.getOrElse(2) { 0 })
        }
    }
}
