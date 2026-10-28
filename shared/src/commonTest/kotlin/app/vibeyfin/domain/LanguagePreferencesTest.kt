package app.vibeyfin.domain

import app.vibeyfin.data.api.ApiError
import app.vibeyfin.data.api.ApiResult
import app.vibeyfin.domain.model.ProfileRef
import app.vibeyfin.testing.FakeServer
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TestApp
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LanguagePreferencesTest {
    private val app = TestApp()
    private val languages = app.languages

    @AfterTest
    fun tearDown() = runTest { app.close() }

    /** A configuration with fields this app doesn't read, which a write must keep. */
    private val config = """{"AudioLanguagePreference":"","SubtitleLanguagePreference":"eng","SubtitleMode":"Always",
        |"PlayDefaultAudioTrack":true,"RememberAudioSelections":true,"RememberSubtitleSelections":false,
        |"DisplayMissingEpisodes":true,"OrderedViews":["a","b"],"CastReceiverId":null,"MyMediaExcludes":[],"HidePlayedInLatest":false}
    """.trimMargin()

    private fun FakeServer.me(configJson: String = config) =
        on("GET", "/Users/Me", Reply.json("""{"Id":"u1","Name":"Alex","Configuration":$configJson}"""))

    /** Accepts configuration writes and keeps the last one as the user's, like the server. */
    private fun FakeServer.acceptWrites() = on("POST", "/Users/Configuration") { request ->
        me(request.body)
        Reply.noContent()
    }

    private suspend fun written(): JsonObject =
        Json.parseToJsonElement(app.jellyfin.requestsTo("POST", "/Users/Configuration").last().body).jsonObject

    @Test
    fun `the preferences are read from the account`() = runTest {
        app.signedIn().me()

        val prefs = assertIs<ApiResult.Success<LanguagePrefs>>(languages.load()).value

        assertEquals(LanguagePrefs(audioLanguage = null, subtitleLanguage = "eng", subtitleMode = SubtitleMode.Always, rememberTracks = false), prefs)
    }

    @Test
    fun `an account that never set them reads as the server's defaults`() = runTest {
        app.signedIn().me("{}")

        assertEquals(LanguagePrefs(), assertIs<ApiResult.Success<LanguagePrefs>>(languages.load()).value)
    }

    @Test
    fun `a mode this build doesn't know reads as Default`() = runTest {
        app.signedIn().me("""{"SubtitleMode":"Sometimes"}""")

        assertEquals(SubtitleMode.Default, assertIs<ApiResult.Success<LanguagePrefs>>(languages.load()).value.subtitleMode)
    }

    @Test
    fun `a write keeps every field it doesn't change`() = runTest {
        val server = app.signedIn()
        server.me()
        server.acceptWrites()

        assertIs<ApiResult.Success<LanguagePrefs>>(languages.setAudioLanguage("ger"))

        val sent = written()
        assertEquals("ger", sent.getValue("AudioLanguagePreference").jsonPrimitive.content)
        assertEquals("eng", sent.getValue("SubtitleLanguagePreference").jsonPrimitive.content)
        assertEquals("Always", sent.getValue("SubtitleMode").jsonPrimitive.content)
        assertTrue(sent.getValue("DisplayMissingEpisodes").jsonPrimitive.boolean)
        assertEquals(2, sent.getValue("OrderedViews").jsonArray.size)
        assertTrue("CastReceiverId" in sent)
        assertEquals("u1", app.jellyfin.requestsTo("POST", "/Users/Configuration").last().query["userId"])
    }

    @Test
    fun `an audio language stops the server preferring the default track and any language lets it again`() = runTest {
        val server = app.signedIn()
        server.me()
        server.acceptWrites()

        languages.setAudioLanguage("ger")
        assertFalse(written().getValue("PlayDefaultAudioTrack").jsonPrimitive.boolean)

        languages.setAudioLanguage(null)
        assertTrue(written().getValue("PlayDefaultAudioTrack").jsonPrimitive.boolean)
        assertEquals("", written().getValue("AudioLanguagePreference").jsonPrimitive.content)
    }

    @Test
    fun `a change leaves the audio keys another client set as they were`() = runTest {
        val server = app.signedIn()
        server.me("""{"AudioLanguagePreference":"ger","PlayDefaultAudioTrack":true,"SubtitleMode":"Default"}""")
        server.acceptWrites()

        languages.setSubtitleMode(SubtitleMode.Smart)

        val sent = written()
        assertEquals("Smart", sent.getValue("SubtitleMode").jsonPrimitive.content)
        assertEquals("ger", sent.getValue("AudioLanguagePreference").jsonPrimitive.content)
        assertTrue(sent.getValue("PlayDefaultAudioTrack").jsonPrimitive.boolean)
        assertFalse("RememberAudioSelections" in sent)
    }

    @Test
    fun `each change writes its own key and returns what was saved`() = runTest {
        val server = app.signedIn()
        server.me()
        server.acceptWrites()

        assertEquals("", (languages.setSubtitleLanguage(null) as ApiResult.Success).value.subtitleLanguage.orEmpty())
        assertEquals("", written().getValue("SubtitleLanguagePreference").jsonPrimitive.content)

        assertEquals(SubtitleMode.Smart, (languages.setSubtitleMode(SubtitleMode.Smart) as ApiResult.Success).value.subtitleMode)
        assertEquals("Smart", written().getValue("SubtitleMode").jsonPrimitive.content)

        assertTrue((languages.setRememberTracks(true) as ApiResult.Success).value.rememberTracks)
        assertTrue(written().getValue("RememberAudioSelections").jsonPrimitive.boolean)
        assertTrue(written().getValue("RememberSubtitleSelections").jsonPrimitive.boolean)
    }

    @Test
    fun `a change reads the configuration again first so another client's change made meanwhile survives`() = runTest {
        val server = app.signedIn()
        server.me()
        languages.load()
        server.me("""{"SubtitleLanguagePreference":"fre","EnableNextEpisodeAutoPlay":false}""")
        server.acceptWrites()

        languages.setSubtitleMode(SubtitleMode.OnlyForced)

        assertEquals("fre", written().getValue("SubtitleLanguagePreference").jsonPrimitive.content)
        assertFalse(written().getValue("EnableNextEpisodeAutoPlay").jsonPrimitive.boolean)
    }

    @Test
    fun `a refused write is a failure and the session stays`() = runTest {
        val server = app.signedIn()
        server.me()
        server.on("POST", "/Users/Configuration", Reply.status(HttpStatusCode.Forbidden))

        assertEquals(ApiError.Forbidden, assertIs<ApiResult.Failure>(languages.setAudioLanguage("ger")).error)
        assertIs<ConnectionState.Connected>(app.connection.state.value)
    }

    @Test
    fun `an unreachable server is a failure and nothing is written`() = runTest {
        val server = app.signedIn()
        server.refuseConnections()

        assertEquals(ApiError.Network, assertIs<ApiResult.Failure>(languages.setAudioLanguage("ger")).error)
        assertTrue(app.jellyfin.requestsTo("POST", "/Users/Configuration").isEmpty())
    }

    @Test
    fun `nobody signed in is unauthorized`() = runTest {
        assertEquals(ApiError.Unauthorized, assertIs<ApiResult.Failure>(languages.load()).error)
    }

    @Test
    fun `the server's languages are named and sorted and listed once each`() = runTest {
        app.signedIn().on(
            "GET", "/Localization/Cultures",
            Reply.json(
                """[{"DisplayName":"German","ThreeLetterISOLanguageName":"ger","ThreeLetterISOLanguageNames":["ger","deu"]},
                |{"DisplayName":"English","ThreeLetterISOLanguageName":"eng","ThreeLetterISOLanguageNames":["eng"]},
                |{"DisplayName":"English (UK)","ThreeLetterISOLanguageName":"eng"},
                |{"DisplayName":"","ThreeLetterISOLanguageName":"xxx"},
                |{"DisplayName":"Nameless code"}]""".trimMargin(),
            ),
        )

        val list = languages.languages()

        assertEquals(listOf("English", "German"), list.map { it.name })
        assertTrue(list.last().matches("deu"))
        assertTrue(list.last().matches("GER"))
        assertFalse(list.first().matches("ger"))
    }

    @Test
    fun `languages that can't be read are an empty list`() = runTest {
        app.signedIn().on("GET", "/Localization/Cultures", Reply.status(HttpStatusCode.InternalServerError))

        assertEquals(emptyList(), languages.languages())
    }

    @Test
    fun `what is read and saved is kept on the device for downloads`() = runTest {
        val server = app.signedIn()
        val alex = ProfileRef("srv-1", "u1")
        server.me()
        server.acceptWrites()

        languages.load()
        assertEquals(LanguagePrefs(subtitleLanguage = "eng", subtitleMode = SubtitleMode.Always, rememberTracks = false), languages.saved(alex))

        languages.setAudioLanguage("ger")
        assertEquals("ger", languages.saved(alex)?.audioLanguage)
    }

    @Test
    fun `a refresh reads the profile's preferences into the device copy and a failed one keeps it`() = runTest {
        val server = app.signedIn()
        val alex = ProfileRef("srv-1", "u1")
        server.me("""{"AudioLanguagePreference":"fre","SubtitleMode":"None"}""")

        languages.refresh(alex)
        assertEquals(LanguagePrefs(audioLanguage = "fre", subtitleMode = SubtitleMode.None), languages.saved(alex))

        server.on("GET", "/Users/Me", Reply.status(HttpStatusCode.InternalServerError))
        languages.refresh(alex)
        assertEquals("fre", languages.saved(alex)?.audioLanguage)
    }

    @Test
    fun `a profile whose preferences were never read has no device copy`() = runTest {
        app.signedIn()

        assertNull(languages.saved(ProfileRef("srv-1", "u1")))
    }

    @Test
    fun `the mode names match the server's`() {
        assertEquals(listOf("Default", "Always", "OnlyForced", "Smart", "None"), SubtitleMode.entries.map { it.server })
        assertEquals(SubtitleMode.OnlyForced, SubtitleMode.fromServer("onlyforced"))
    }
}
