package app.vibeyfin.domain

/** The audio and subtitle track a video starts with; a null subtitle is none. */
data class TrackChoice(val audioIndex: Int?, val subtitleIndex: Int?)

/**
 * The tracks Jellyfin would start [audio] and [subtitles] with for [prefs], worked out on the device for a download that plays
 * without the server. It follows the server's own rules (`MediaStreamSelector`): tracks in the preferred language first, then
 * the file's default, then the file's order. Downloaded subtitles are files next to the video, which the server counts as
 * external, so the Default mode shows the first of them.
 */
fun chooseTracks(audio: List<PlaybackTrack>, subtitles: List<PlaybackTrack>, prefs: LanguagePrefs): TrackChoice {
    val chosenAudio = audio.sortedFor(prefs.audioLanguage).firstOrNull()
    val audioLanguage = chosenAudio?.language
    val sorted = subtitles.sortedFor(prefs.subtitleLanguage)
    val preferred = prefs.subtitleLanguage

    fun PlaybackTrack.isPreferred() = preferred == null || sameLanguage(language, preferred)
    val byFlags = sorted.firstOrNull()
    val subtitle = when (prefs.subtitleMode) {
        SubtitleMode.None -> null

        SubtitleMode.Default -> byFlags

        SubtitleMode.Always -> sorted.firstOrNull { !it.forced && it.isPreferred() } ?: byFlags

        SubtitleMode.Smart ->
            if (preferred == null || !sameLanguage(audioLanguage, preferred)) {
                sorted.firstOrNull { it.isPreferred() } ?: byFlags
            } else {
                sorted.firstOrNull { it.forced }
            }

        SubtitleMode.OnlyForced -> sorted.firstOrNull { it.forced && preferred != null && sameLanguage(it.language, preferred) }
            ?: sorted.firstOrNull { it.forced && it.language == null }
    }
    return TrackChoice(chosenAudio?.index, subtitle?.index)
}

// In the preferred [language] first, then the file's default, then the file's order.
private fun List<PlaybackTrack>.sortedFor(language: String?): List<PlaybackTrack> = sortedWith(
    compareBy<PlaybackTrack> { language == null || !sameLanguage(it.language, language) }
        .thenBy { !it.default }
        .thenBy { it.index },
)

/** Whether two ISO 639-2 codes name one language, counting both codes of a language that has two (`ger` and `deu`). */
internal fun sameLanguage(a: String?, b: String?): Boolean {
    if (a == null || b == null) return false
    val x = a.lowercase()
    val y = b.lowercase()
    return x == y || BIBLIOGRAPHIC_TO_TERMINOLOGY[x] == y || BIBLIOGRAPHIC_TO_TERMINOLOGY[y] == x
}

// The languages ISO 639-2 gives two codes: the bibliographic one and the terminology one.
private val BIBLIOGRAPHIC_TO_TERMINOLOGY = mapOf(
    "alb" to "sqi", "arm" to "hye", "baq" to "eus", "bur" to "mya", "chi" to "zho", "cze" to "ces", "dut" to "nld",
    "fre" to "fra", "geo" to "kat", "ger" to "deu", "gre" to "ell", "ice" to "isl", "mac" to "mkd", "mao" to "mri",
    "may" to "msa", "per" to "fas", "rum" to "ron", "slo" to "slk", "tib" to "bod", "wel" to "cym",
)
