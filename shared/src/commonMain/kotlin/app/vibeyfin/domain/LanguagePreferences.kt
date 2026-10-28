package app.vibeyfin.domain

import app.vibeyfin.data.api.ApiError
import app.vibeyfin.data.api.ApiResult
import app.vibeyfin.data.api.CultureDto
import app.vibeyfin.data.api.valueOrNull
import app.vibeyfin.data.db.AccountDao
import app.vibeyfin.domain.model.ProfileRef
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/** When subtitles show, as Jellyfin's `SubtitlePlaybackMode`; [server] is the name the server stores. */
enum class SubtitleMode(val server: String) {
    /** Subtitles the video marks as default or forced. */
    Default("Default"),

    /** Whenever there are subtitles in the preferred language. */
    Always("Always"),

    /** Only forced subtitles, for foreign dialogue. */
    OnlyForced("OnlyForced"),

    /** Only when the audio isn't in the preferred language. */
    Smart("Smart"),

    /** Never. */
    None("None"),
    ;

    companion object {
        /** A mode this build doesn't know reads as [Default], the server's own default. */
        fun fromServer(value: String?): SubtitleMode = entries.firstOrNull { it.server.equals(value, ignoreCase = true) } ?: Default
    }
}

/**
 * A language the server knows: [code] is the ISO 639-2 code it stores as a preference, [otherCodes] the language's other ISO
 * 639-2 code where it has two (`ger` and `deu`), since tracks and other clients may use either.
 */
data class Language(val code: String, val name: String, val otherCodes: List<String> = emptyList()) {
    /** Whether [code] names this language, by either of its codes. */
    fun matches(code: String): Boolean = this.code.equals(code, ignoreCase = true) || otherCodes.any { it.equals(code, ignoreCase = true) }
}

/**
 * Which tracks a video starts with, from the Jellyfin user's configuration. A null language is no preference: the video's
 * default track. [rememberTracks] is the server remembering the tracks picked in a player for the next video of a series.
 */
@Serializable
data class LanguagePrefs(
    val audioLanguage: String? = null,
    val subtitleLanguage: String? = null,
    val subtitleMode: SubtitleMode = SubtitleMode.Default,
    val rememberTracks: Boolean = true,
)

/**
 * The signed-in user's preferred audio and subtitle languages. They live on the Jellyfin account, not the device, so the
 * server picks a video's default tracks from them and every Jellyfin app shares them. Each change reads the configuration
 * again and writes it back whole, with only these keys changed, so no other client's setting is lost. A copy of what was
 * last read is kept with the profile on the device, only so downloads can pick the same tracks without the server.
 */
class LanguagePreferences(
    private val connection: ConnectionManager,
    private val dao: AccountDao,
) {
    /** The preferences as the server has them now. */
    suspend fun load(): ApiResult<LanguagePrefs> = when (val config = configuration()) {
        is ApiResult.Success -> ApiResult.Success(config.value.second.toPrefs().also { keep(it) })
        is ApiResult.Failure -> config
    }

    /** The copy on the device of [profile]'s preferences; null when they have never been read. */
    suspend fun saved(profile: ProfileRef): LanguagePrefs? =
        dao.getLanguagePrefs(profile.serverId, profile.userId)?.let { runCatching { JSON.decodeFromString(LanguagePrefs.serializer(), it) }.getOrNull() }

    /** Reads [profile]'s preferences from the server into the copy on the device; leaves the copy as it was when that fails. */
    suspend fun refresh(profile: ProfileRef) {
        val call = connection.asProfile(profile.serverId, profile.userId) { it.me() }
        val config = ((call as? ProfileCall.Answered)?.result as? ApiResult.Success)?.value?.configuration ?: return
        keep(profile, config.toPrefs())
    }

    /**
     * The languages the server knows, sorted by name, one entry per language. Empty when they can't be read; the screens then
     * fall back to the codes.
     */
    suspend fun languages(): List<Language> {
        val connected = connection.state.value as? ConnectionState.Connected ?: return emptyList()
        return connected.api.cultures().valueOrNull().orEmpty().toLanguages()
    }

    /** Null is no preference. */
    suspend fun setAudioLanguage(code: String?) = update { it.copy(audioLanguage = code?.ifBlank { null }) }

    /** Null is no preference. */
    suspend fun setSubtitleLanguage(code: String?) = update { it.copy(subtitleLanguage = code?.ifBlank { null }) }

    suspend fun setSubtitleMode(mode: SubtitleMode) = update { it.copy(subtitleMode = mode) }

    suspend fun setRememberTracks(remember: Boolean) = update { it.copy(rememberTracks = remember) }

    /** Reads the configuration, applies [change] to our keys only, and writes all of it back. Returns what was saved. */
    private suspend fun update(change: (LanguagePrefs) -> LanguagePrefs): ApiResult<LanguagePrefs> {
        val connected = connection.state.value as? ConnectionState.Connected ?: return ApiResult.Failure(ApiError.Unauthorized)
        val (userId, config) = when (val read = configuration()) {
            is ApiResult.Success -> read.value
            is ApiResult.Failure -> return read
        }
        val prefs = change(config.toPrefs())
        return when (val written = connected.api.updateUserConfiguration(userId, config.with(prefs))) {
            is ApiResult.Success -> ApiResult.Success(prefs.also { keep(it) })
            is ApiResult.Failure -> written
        }
    }

    /** Keeps [prefs] as the signed-in profile's copy on the device. */
    private suspend fun keep(prefs: LanguagePrefs) {
        val account = (connection.state.value as? ConnectionState.Connected)?.account ?: return
        keep(ProfileRef(account.serverId, account.userId), prefs)
    }

    private suspend fun keep(profile: ProfileRef, prefs: LanguagePrefs) =
        dao.setLanguagePrefs(profile.serverId, profile.userId, JSON.encodeToString(LanguagePrefs.serializer(), prefs))

    /** The signed-in user's id and whole configuration. */
    private suspend fun configuration(): ApiResult<Pair<String, JsonObject>> {
        val connected = connection.state.value as? ConnectionState.Connected ?: return ApiResult.Failure(ApiError.Unauthorized)
        return when (val me = connected.api.me()) {
            // A signed-in user always has one; without it there would be nothing to write back whole.
            is ApiResult.Success -> me.value.configuration?.let { ApiResult.Success(me.value.id to it) } ?: ApiResult.Failure(ApiError.BadResponse)
            is ApiResult.Failure -> me
        }
    }

    internal companion object {
        const val AUDIO_LANGUAGE = "AudioLanguagePreference"
        const val SUBTITLE_LANGUAGE = "SubtitleLanguagePreference"
        const val SUBTITLE_MODE = "SubtitleMode"
        const val PLAY_DEFAULT_AUDIO = "PlayDefaultAudioTrack"
        const val REMEMBER_AUDIO = "RememberAudioSelections"
        const val REMEMBER_SUBTITLES = "RememberSubtitleSelections"

        private val JSON = Json { ignoreUnknownKeys = true }

        fun JsonObject.toPrefs(): LanguagePrefs {
            fun string(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            fun flag(key: String) = (this[key] as? JsonPrimitive)?.booleanOrNull ?: true
            return LanguagePrefs(
                audioLanguage = string(AUDIO_LANGUAGE),
                subtitleLanguage = string(SUBTITLE_LANGUAGE),
                subtitleMode = SubtitleMode.fromServer(string(SUBTITLE_MODE)),
                rememberTracks = flag(REMEMBER_AUDIO) && flag(REMEMBER_SUBTITLES),
            )
        }

        /**
         * This configuration with [prefs] written in. With an audio language set, the server must not prefer the file's default
         * track over it, so `PlayDefaultAudioTrack` goes off; with none it goes back on, the server's default.
         */
        fun JsonObject.with(prefs: LanguagePrefs): JsonObject = JsonObject(
            this + mapOf(
                AUDIO_LANGUAGE to JsonPrimitive(prefs.audioLanguage.orEmpty()),
                SUBTITLE_LANGUAGE to JsonPrimitive(prefs.subtitleLanguage.orEmpty()),
                SUBTITLE_MODE to JsonPrimitive(prefs.subtitleMode.server),
                PLAY_DEFAULT_AUDIO to JsonPrimitive(prefs.audioLanguage == null),
                REMEMBER_AUDIO to JsonPrimitive(prefs.rememberTracks),
                REMEMBER_SUBTITLES to JsonPrimitive(prefs.rememberTracks),
            ),
        )

        /** Named, coded, sorted by name, and one entry per code (the server lists some languages under several cultures). */
        fun List<CultureDto>.toLanguages(): List<Language> = mapNotNull { culture ->
            val code = culture.threeLetterIsoLanguageName?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val name = culture.displayName?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Language(code, name, culture.threeLetterIsoLanguageNames.orEmpty().filterNot { it.equals(code, ignoreCase = true) })
        }.distinctBy { it.code }.sortedBy { it.name.lowercase() }
    }
}

