package app.vibeyfin.ui.settings

import androidx.lifecycle.viewModelScope
import app.vibeyfin.domain.ConnectionState
import app.vibeyfin.domain.ProfileSettings
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.ViewModelTracker
import app.vibeyfin.ui.common.PinReason
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.testing.authJson
import io.ktor.http.HttpStatusCode
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val app = TestApp()
    private val viewModels = ViewModelTracker()
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() = runTest {
        viewModels.stopAll()
        applicationScope.cancel()
        Dispatchers.resetMain()
        app.close()
    }

    private fun SettingsUiState.item(key: SettingKey) = pages.values.flatMap { page -> page.sections.flatMap { it.items } }.first { it.key == key }

    private fun SettingsUiState.checked(key: SettingKey) = (item(key) as ToggleItem).checked

    private fun SettingsUiState.value(key: SettingKey) = (item(key) as ChoiceItem).value

    private fun viewModel() = viewModels.track(
        SettingsViewModel(app.preferences, app.profileSettings, app.connection, app.servers, app.pins, app.auth, applicationScope),
    )

    @Test
    fun alwaysAskStartsOff() = runTest {
        assertFalse(viewModel().uiState.first().checked(SettingKey.AlwaysAsk))
    }

    @Test
    fun theToggleIsSavedOnTheDevice() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.first()

        viewModel.onToggle(SettingKey.AlwaysAsk, true)

        assertTrue(viewModel.uiState.first { it.checked(SettingKey.AlwaysAsk) }.checked(SettingKey.AlwaysAsk))
        assertTrue(app.preferences.alwaysAskWhoIsWatching.first())
    }

    @Test
    fun theToggleCanBeTurnedOffAgain() = runTest {
        app.preferences.setAlwaysAskWhoIsWatching(true)
        val viewModel = viewModel()
        assertTrue(viewModel.uiState.first { it.checked(SettingKey.AlwaysAsk) }.checked(SettingKey.AlwaysAsk))

        viewModel.onToggle(SettingKey.AlwaysAsk, false)

        assertEquals(false, viewModel.uiState.first { !it.checked(SettingKey.AlwaysAsk) }.checked(SettingKey.AlwaysAsk))
    }

    @Test
    fun withNobodySignedInThereAreNoProfileSettings() = runTest {
        assertFalse(viewModel().uiState.first().hasProfile)
    }

    @Test
    fun `hasProfile is true for a signed-in profile`() = runTest {
        app.signedIn()

        assertTrue(viewModel().uiState.first { it.hasProfile }.hasProfile)
    }

    @Test
    fun `defaults are stats off and three in a row`() = runTest {
        app.signedIn()
        val state = viewModel().uiState.first { it.hasProfile }

        assertFalse(state.checked(SettingKey.ShowStats))
        assertEquals(3, state.value(SettingKey.BingeThreshold))
    }

    @Test
    fun `changing the threshold is stored per profile`() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Users/Me", Reply.json("""{"Id":"u2","Name":"Sam"}"""))
        app.accounts.saveSession("srv-1", "u2", "Sam", null, "token-2")
        val viewModel = viewModel()

        app.connection.activate("srv-1", "u1")
        viewModel.uiState.first { it.hasProfile }
        viewModel.onChoose(SettingKey.BingeThreshold, 5)
        viewModel.uiState.first { it.value(SettingKey.BingeThreshold) == 5 }

        app.connection.activate("srv-1", "u2")
        assertEquals(3, app.profileSettings.bingeThreshold.first())
        viewModel.onChoose(SettingKey.BingeThreshold, 0)
        viewModel.uiState.first { it.value(SettingKey.BingeThreshold) == 0 }

        app.connection.activate("srv-1", "u1")
        assertEquals(5, app.profileSettings.bingeThreshold.first())
    }

    @Test
    fun `a threshold outside 0 or 2 to 5 is ignored`() = runTest {
        app.signedIn()
        val viewModel = viewModel()
        viewModel.uiState.first { it.hasProfile }

        for (invalid in listOf(1, 6, -1, 100)) {
            viewModel.onChoose(SettingKey.BingeThreshold, invalid)
            assertEquals(3, app.profileSettings.bingeThreshold.first())
        }
    }

    @Test
    fun `a new profile starts with the top five genre rows and can change it`() = runTest {
        app.signedIn()
        val viewModel = viewModel()
        assertEquals(5, viewModel.uiState.first { it.hasProfile }.value(SettingKey.HomeGenreRows))

        viewModel.onChoose(SettingKey.HomeGenreRows, 0)
        assertEquals(0, viewModel.uiState.first { it.value(SettingKey.HomeGenreRows) == 0 }.value(SettingKey.HomeGenreRows))
        viewModel.onChoose(SettingKey.HomeGenreRows, -1)
        assertEquals(-1, viewModel.uiState.first { it.value(SettingKey.HomeGenreRows) == -1 }.value(SettingKey.HomeGenreRows))
    }

    @Test
    fun `a genre row count that is not one of the choices is ignored`() = runTest {
        app.signedIn()
        val viewModel = viewModel()
        viewModel.uiState.first { it.hasProfile }

        for (invalid in listOf(1, 4, 7, 21, -2)) {
            viewModel.onChoose(SettingKey.HomeGenreRows, invalid)
            assertEquals(5, app.profileSettings.homeGenreRows.first())
        }
    }

    @Test
    fun `the home hero is on until the profile turns it off`() = runTest {
        app.signedIn()
        val viewModel = viewModel()
        assertTrue(viewModel.uiState.first { it.hasProfile }.checked(SettingKey.ShowHero))

        viewModel.onToggle(SettingKey.ShowHero, false)
        assertFalse(viewModel.uiState.first { !it.checked(SettingKey.ShowHero) }.checked(SettingKey.ShowHero))
        viewModel.onToggle(SettingKey.ShowHero, true)
        assertTrue(viewModel.uiState.first { it.checked(SettingKey.ShowHero) }.checked(SettingKey.ShowHero))
    }

    @Test
    fun theStatsToggleIsSavedForTheProfile() = runTest {
        app.signedIn()
        val viewModel = viewModel()
        viewModel.uiState.first { it.hasProfile }

        viewModel.onToggle(SettingKey.ShowStats, true)

        assertTrue(viewModel.uiState.first { it.checked(SettingKey.ShowStats) }.checked(SettingKey.ShowStats))
    }

    @Test
    fun `skip settings default to 10 and 10 and auto-skip off`() = runTest {
        app.signedIn()
        val state = viewModel().uiState.first { it.hasProfile }

        assertEquals(10, state.value(SettingKey.SkipBack))
        assertEquals(10, state.value(SettingKey.SkipForward))
        assertFalse(state.checked(SettingKey.AutoSkip))
    }

    @Test
    fun `each skip handler is reflected in the state`() = runTest {
        app.signedIn()
        val viewModel = viewModel()
        viewModel.uiState.first { it.hasProfile }

        viewModel.onChoose(SettingKey.SkipBack, 30)
        assertEquals(30, viewModel.uiState.first { it.value(SettingKey.SkipBack) == 30 }.value(SettingKey.SkipBack))
        viewModel.onChoose(SettingKey.SkipForward, 45)
        assertEquals(45, viewModel.uiState.first { it.value(SettingKey.SkipForward) == 45 }.value(SettingKey.SkipForward))
        viewModel.onToggle(SettingKey.AutoSkip, true)
        assertTrue(viewModel.uiState.first { it.checked(SettingKey.AutoSkip) }.checked(SettingKey.AutoSkip))
    }

    @Test
    fun `buffer ahead defaults to 20 and follows its handler`() = runTest {
        app.signedIn()
        val viewModel = viewModel()
        assertEquals(20, viewModel.uiState.first { it.hasProfile }.value(SettingKey.BufferAhead))

        viewModel.onChoose(SettingKey.BufferAhead, 60)
        assertEquals(60, viewModel.uiState.first { it.value(SettingKey.BufferAhead) == 60 }.value(SettingKey.BufferAhead))
    }

    @Test
    fun `switching profile shows that profile's skip settings or the defaults`() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Users/Me", Reply.json("""{"Id":"u2","Name":"Sam"}"""))
        app.accounts.saveSession("srv-1", "u2", "Sam", null, "token-2")
        val viewModel = viewModel()

        app.connection.activate("srv-1", "u1")
        viewModel.uiState.first { it.hasProfile }
        viewModel.onChoose(SettingKey.SkipBack, 30)
        viewModel.onChoose(SettingKey.SkipForward, 45)
        viewModel.onToggle(SettingKey.AutoSkip, true)
        viewModel.uiState.first { it.value(SettingKey.SkipBack) == 30 && it.value(SettingKey.SkipForward) == 45 && it.checked(SettingKey.AutoSkip) }

        app.connection.activate("srv-1", "u2")
        val other = viewModel.uiState.first { it.value(SettingKey.SkipBack) == 10 }
        assertEquals(10, other.value(SettingKey.SkipForward))
        assertFalse(other.checked(SettingKey.AutoSkip))

        app.connection.activate("srv-1", "u1")
        val back = viewModel.uiState.first { it.value(SettingKey.SkipBack) == 30 }
        assertEquals(45, back.value(SettingKey.SkipForward))
        assertTrue(back.checked(SettingKey.AutoSkip))
    }

    private fun SettingsUiState.page(id: SettingsPageId) = pages.getValue(id).visibleOnly()

    private fun SettingsPage.keys() = sections.flatMap { section -> section.items.map { it.key } }

    @Test
    fun `the root has its six groups in the designed order`() = runTest {
        app.signedIn()
        val root = viewModel().uiState.first { it.hasProfile }.page(SettingsPageId.Root)

        assertEquals(
            listOf(
                Strings.playbackSection, Strings.settingsGroupHome, Strings.notificationsTitle,
                Strings.settingsGroupKids, Strings.settingsGroupDevice, Strings.settingsGroupAdvanced,
            ),
            root.sections.mapNotNull { it.title },
        )
    }

    @Test
    fun `the root Playback group links to Quality and Subtitles and Skipping only`() = runTest {
        app.signedIn()
        val root = viewModel().uiState.first { it.hasProfile }.page(SettingsPageId.Root)

        val playback = root.sections.single { it.title == Strings.playbackSection }
        assertTrue(playback.items.all { it is LinkItem })
        assertEquals(listOf(SettingKey.Quality, SettingKey.Subtitles, SettingKey.Skipping), playback.items.map { it.key })
        assertFalse(root.keys().contains(SettingKey.BufferAhead))
    }

    @Test
    fun `the stats overlay lives in Advanced`() = runTest {
        app.signedIn()
        val root = viewModel().uiState.first { it.hasProfile }.page(SettingsPageId.Root)

        val holding = root.sections.filter { section -> section.items.any { it.key == SettingKey.ShowStats } }
        assertEquals(listOf(Strings.settingsGroupAdvanced), holding.map { it.title })
    }

    @Test
    fun `skip and auto skip still watching and buffering sit on the Skipping page`() = runTest {
        app.signedIn()
        val viewModel = viewModel()
        val skipping = viewModel.uiState.first { it.hasProfile }.page(SettingsPageId.Skipping)

        assertEquals(
            listOf(SettingKey.SkipBack, SettingKey.SkipForward, SettingKey.AutoSkip, SettingKey.BingeThreshold, SettingKey.BufferAhead),
            skipping.keys(),
        )

        viewModel.onChoose(SettingKey.SkipBack, 30)
        viewModel.onToggle(SettingKey.AutoSkip, true)
        viewModel.onChoose(SettingKey.BufferAhead, 60)
        val changed = viewModel.uiState.first { it.value(SettingKey.BufferAhead) == 60 }.page(SettingsPageId.Skipping)
        val items = changed.sections.flatMap { it.items }.associateBy { it.key }
        assertEquals(30, (items.getValue(SettingKey.SkipBack) as ChoiceItem).value)
        assertTrue((items.getValue(SettingKey.AutoSkip) as ToggleItem).checked)
        assertEquals(60, (items.getValue(SettingKey.BufferAhead) as ChoiceItem).value)
    }

    @Test
    fun `choosing a quality saves it and the Quality page checks it`() = runTest {
        app.signedIn()
        val viewModel = viewModel()
        val before = viewModel.uiState.first { it.hasProfile }.page(SettingsPageId.Quality)
        val choice = before.sections.single().items.single() as ChoiceItem
        assertEquals(ProfileSettings.QUALITY_CHOICES, choice.options.map { it.value })
        assertEquals(0, choice.value)

        viewModel.onChoose(SettingKey.Quality, 10_000_000)

        val after = viewModel.uiState.first {
            (it.page(SettingsPageId.Quality).sections.single().items.single() as ChoiceItem).value == 10_000_000
        }
        assertEquals(10_000_000, app.profileSettings.maxStreamingBitrate.first())
        val link = after.page(SettingsPageId.Root).sections.flatMap { it.items }.first { it.key == SettingKey.Quality } as LinkItem
        assertEquals(Strings.qualityLabel(10_000_000), link.value)
    }

    @Test
    fun `device settings show without a profile and profile settings do not`() = runTest {
        val signedOut = viewModel().uiState.first().page(SettingsPageId.Root)
        assertTrue(SettingKey.AlwaysAsk in signedOut.keys())
        assertEquals(emptyList(), signedOut.keys() - setOf(SettingKey.AlwaysAsk, SettingKey.About))

        app.signedIn()
        val signedIn = viewModel().uiState.first { it.hasProfile }.page(SettingsPageId.Root)
        assertTrue(SettingKey.AlwaysAsk in signedIn.keys())
        assertTrue(SettingKey.ShowStats in signedIn.keys())
        assertTrue(SettingKey.ShowHero in signedIn.keys())
    }

    private fun SettingsUiState.items(id: SettingsPageId) = page(id).sections.flatMap { it.items }

    @Test
    fun `the profile page shows the server name and addresses`() = runTest {
        app.signedIn()
        app.servers.save("srv-1", "Home", "10.10.7", "http://nas:8096", listOf("http://192.168.1.20:8096"))
        val state = viewModel().uiState.first { it.hasProfile && it.items(SettingsPageId.Profile).any { i -> i.key == SettingKey.ServerAlsoAt } }

        val items = state.items(SettingsPageId.Profile).associateBy { it.key }
        val header = items.getValue(SettingKey.ProfileCard) as CustomItem
        assertEquals(CustomKind.ProfileHeader, header.kind)
        assertEquals("Alex", header.title)
        assertEquals("http://nas:8096", header.help)
        assertEquals("Home", (items.getValue(SettingKey.ServerName) as InfoItem).value)
        assertEquals("http://nas:8096", (items.getValue(SettingKey.ServerAddress) as InfoItem).value)
        assertEquals("http://192.168.1.20:8096", (items.getValue(SettingKey.ServerAlsoAt) as InfoItem).value)
        assertTrue(SettingKey.SwitchProfile in items)
        assertTrue((items.getValue(SettingKey.SignOut) as ActionItem).destructive)
    }

    @Test
    fun `a single address leaves out the also reachable row`() = runTest {
        app.signedIn()
        val state = viewModel().uiState.first { it.hasProfile && it.items(SettingsPageId.Profile).any { i -> i.key == SettingKey.ServerName } }

        assertFalse(SettingKey.ServerAlsoAt in state.items(SettingsPageId.Profile).map { it.key })
    }

    @Test
    fun `the profile page is hidden without a profile and sign out is hidden on a kids profile`() = runTest {
        assertEquals(emptyList(), viewModel().uiState.first().items(SettingsPageId.Profile).map { it.key })

        val values = SettingsValues(hasProfile = true, kidsProfile = true, profileName = "Alex", serverName = "Home", serverAddresses = listOf("a"))
        val kids = profilePage(values).visibleOnly().sections.flatMap { it.items }.map { it.key }
        assertFalse(SettingKey.SignOut in kids)
        assertTrue(SettingKey.SwitchProfile in kids)
    }

    @Test
    fun `signing out asks to confirm and then clears the session`() = runTest {
        val server = app.signedIn()
        server.on("POST", "/Sessions/Logout", Reply.noContent())
        val viewModel = viewModel()
        viewModel.uiState.first { it.hasProfile }

        viewModel.onAction(SettingKey.SignOut)
        assertTrue(viewModel.uiState.first { it.confirmSignOut }.confirmSignOut)
        assertTrue(app.connection.state.value is ConnectionState.Connected)

        viewModel.onConfirmSignOut()
        assertFalse(viewModel.uiState.first { !it.confirmSignOut }.confirmSignOut)
        assertEquals(ConnectionState.Disconnected, app.connection.state.value)
        assertEquals(1, app.jellyfin.requestsTo("POST", "/Sessions/Logout").size)
        assertFalse(app.accounts.account("srv-1", "u1")!!.hasSession)
    }

    @Test
    fun `sign-out finishes even when the view model is cleared right away`() = runTest {
        val server = app.signedIn()
        val logout = server.hold("POST", "/Sessions/Logout")
        server.on("POST", "/Sessions/Logout", Reply.noContent())
        val viewModel = viewModel()
        viewModel.uiState.first { it.hasProfile }

        viewModel.onConfirmSignOut()
        viewModel.viewModelScope.cancel()
        logout.open()

        withContext(Dispatchers.Default) {
            withTimeout(5_000) {
                while (app.accounts.account("srv-1", "u1")!!.hasSession) delay(10)
            }
        }
        assertEquals(1, app.jellyfin.requestsTo("POST", "/Sessions/Logout").size)
        assertEquals(ConnectionState.Disconnected, app.connection.state.value)
    }

    @Test
    fun `cancelling the confirmation keeps the session`() = runTest {
        val server = app.signedIn()
        server.on("POST", "/Sessions/Logout", Reply.noContent())
        val viewModel = viewModel()
        viewModel.uiState.first { it.hasProfile }

        viewModel.onAction(SettingKey.SignOut)
        viewModel.uiState.first { it.confirmSignOut }
        viewModel.onDismissSignOut()

        assertFalse(viewModel.uiState.first { !it.confirmSignOut }.confirmSignOut)
        assertTrue(app.connection.state.value is ConnectionState.Connected)
        assertEquals(0, app.jellyfin.requestsTo("POST", "/Sessions/Logout").size)
    }

    @Test
    fun `the sign-out confirmation closes when the connection drops`() = runTest {
        app.signedIn()
        val viewModel = viewModel()
        viewModel.uiState.first { it.hasProfile }
        viewModel.onAction(SettingKey.SignOut)
        viewModel.uiState.first { it.confirmSignOut }

        app.connection.switchProfile()

        assertFalse(viewModel.uiState.first { !it.confirmSignOut }.confirmSignOut)
    }

    @Test
    fun `switching profile leaves the profile signed in and disconnects`() = runTest {
        app.signedIn()
        val viewModel = viewModel()
        viewModel.uiState.first { it.hasProfile }

        viewModel.onAction(SettingKey.SwitchProfile)

        assertEquals(ConnectionState.Disconnected, app.connection.state.value)
        assertTrue(app.accounts.account("srv-1", "u1")!!.hasSession)
    }

    @Test
    fun `only Quality is drawn as a checklist page`() = runTest {
        app.signedIn()
        val state = viewModel().uiState.first { it.hasProfile }

        assertTrue((state.items(SettingsPageId.Quality).single() as ChoiceItem).asPage)
        val skipping = state.items(SettingsPageId.Skipping).filterIsInstance<ChoiceItem>()
        assertTrue(skipping.isNotEmpty())
        assertTrue(skipping.none { it.asPage })
    }

    @Test
    fun `options refuses choices and labels of different lengths`() {
        assertFailsWith<IllegalArgumentException> { options(listOf(1, 2, 3), "a", "b") }
        assertEquals(listOf(SettingOption(1, "a")), options(listOf(1), "a"))
    }

    @Test
    fun `the style rows show only when system captions are off`() = runTest {
        app.signedIn()
        val viewModel = viewModel()
        val styleKeys = setOf(SettingKey.SubtitleSize, SettingKey.SubtitleColor, SettingKey.SubtitleBackground, SettingKey.ResetSubtitles)

        val system = viewModel.uiState.first { it.hasProfile }.page(SettingsPageId.Subtitles).keys()
        assertTrue(SettingKey.CaptionsFollowSystem in system)
        assertTrue(SettingKey.SubtitlePreview in system)
        assertTrue(system.none { it in styleKeys })

        viewModel.onToggle(SettingKey.CaptionsFollowSystem, false)
        val custom = viewModel.uiState.first { !it.page(SettingsPageId.Subtitles).keys().none { k -> k in styleKeys } }
            .page(SettingsPageId.Subtitles).keys()
        assertTrue(custom.containsAll(styleKeys))
    }

    @Test
    fun `the subtitle preview is a section of its own before the toggle`() = runTest {
        app.signedIn()
        val page = viewModel().uiState.first { it.hasProfile }.page(SettingsPageId.Subtitles).visibleOnly()
        assertEquals(listOf(SettingKey.SubtitlePreview), page.sections[0].items.map { it.key })
        assertEquals(null, page.sections[0].title)
        assertEquals(SettingKey.CaptionsFollowSystem, page.sections[1].items.first().key)
    }

    @Test
    fun `choosing size and colour and background is stored and reset restores the defaults`() = runTest {
        app.signedIn()
        val viewModel = viewModel()
        viewModel.uiState.first { it.hasProfile }

        viewModel.onChoose(SettingKey.SubtitleSize, ProfileSettings.SUBTITLE_SIZE_CHOICES.last())
        viewModel.onChoose(SettingKey.SubtitleColor, ProfileSettings.SUBTITLE_COLOR_CHOICES.last())
        viewModel.onChoose(SettingKey.SubtitleBackground, ProfileSettings.SUBTITLE_BACKGROUND_CHOICES.last())
        assertEquals(ProfileSettings.SUBTITLE_SIZE_CHOICES.last(), app.profileSettings.subtitleSize.first())
        assertEquals(ProfileSettings.SUBTITLE_COLOR_CHOICES.last(), app.profileSettings.subtitleTextColor.first())
        assertEquals(ProfileSettings.SUBTITLE_BACKGROUND_CHOICES.last(), app.profileSettings.subtitleBackground.first())

        viewModel.onAction(SettingKey.ResetSubtitles)
        assertEquals(1, app.profileSettings.subtitleSize.first())
        assertEquals(0, app.profileSettings.subtitleTextColor.first())
        assertEquals(1, app.profileSettings.subtitleBackground.first())
    }

    @Test
    fun `a quality that is not a choice leaves the stored quality unchanged`() = runTest {
        app.signedIn()
        val viewModel = viewModel()
        viewModel.uiState.first { it.hasProfile }

        viewModel.onChoose(SettingKey.Quality, 123)

        assertEquals(0, app.profileSettings.maxStreamingBitrate.first())
    }

    /** Signed in as Alex, with "1234" as the PIN when [pin] is true; the switches are on unless turned off. */
    private suspend fun signedInWithPin(pin: Boolean = true) = app.signedIn().also {
        if (pin) app.setPin("u1", "1234")
        // "right" is Alex's Jellyfin password.
        it.on("POST", "/Users/AuthenticateByName") { request ->
            if (request.body.contains(""""Pw":"right"""")) Reply.json(authJson(token = "token-2")) else Reply.status(HttpStatusCode.Unauthorized)
        }
    }

    private suspend fun SettingsViewModel.open() = uiState.first { it.hasProfile && !it.locked }

    @Test
    fun `a profile without a PIN or kids flag sees no prompt anywhere`() = runTest {
        signedInWithPin(pin = false)
        val viewModel = viewModel()
        assertNull(viewModel.open().pinPrompt)

        viewModel.onToggle(SettingKey.KidsProfile, true)
        app.profileSettings.kidsProfile.first { it == true }
        viewModel.onToggle(SettingKey.KidsProfile, false)
        app.profileSettings.kidsProfile.first { it == false }
        viewModel.onAction(SettingKey.SwitchProfile)

        assertNull(viewModel.uiState.value.pinPrompt)
        assertEquals(ConnectionState.Disconnected, app.connection.state.value)
    }

    @Test
    fun `opening Settings asks for the PIN when the switch is on and opens after the right one`() = runTest {
        signedInWithPin()
        val viewModel = viewModel()
        val asked = viewModel.uiState.first { it.pinPrompt != null }
        assertEquals(PinReason.OpenSettings, asked.pinPrompt!!.reason)
        assertTrue(asked.locked)

        viewModel.onSubmitPin("0000")
        val wrong = viewModel.uiState.first { it.pinPrompt?.error != null }
        assertEquals(Strings.pinPromptWrong, wrong.pinPrompt!!.error)
        assertTrue(wrong.locked)

        viewModel.onSubmitPin("1234")
        val open = viewModel.uiState.first { !it.locked }
        assertNull(open.pinPrompt)
    }

    @Test
    fun `dismissing the Settings PIN leaves Settings`() = runTest {
        signedInWithPin()
        val viewModel = viewModel()
        viewModel.uiState.first { it.pinPrompt != null }

        viewModel.onDismissPin()

        val state = viewModel.uiState.first { it.leave }
        assertTrue(state.locked)
        assertNull(state.pinPrompt)
    }

    @Test
    fun `switching profile asks for the PIN when the switch is on`() = runTest {
        signedInWithPin()
        app.database.accountDao().setPinOpenSettings("srv-1", "u1", false)
        val viewModel = viewModel()
        viewModel.open()

        viewModel.onAction(SettingKey.SwitchProfile)
        assertEquals(PinReason.SwitchProfile, viewModel.uiState.first { it.pinPrompt != null }.pinPrompt!!.reason)
        assertTrue(app.connection.state.value is ConnectionState.Connected)

        viewModel.onSubmitPin("1234")
        app.connection.state.first { it == ConnectionState.Disconnected }
    }

    @Test
    fun `turning kids mode off asks for the PIN even when every switch is off`() = runTest {
        signedInWithPin()
        val dao = app.database.accountDao()
        dao.setPinOpenProfile("srv-1", "u1", false)
        dao.setPinOpenSettings("srv-1", "u1", false)
        dao.setPinSwitchProfile("srv-1", "u1", false)
        app.profileSettings.setKidsProfile(true)
        val viewModel = viewModel()
        assertNull(viewModel.open().pinPrompt)

        viewModel.onToggle(SettingKey.KidsProfile, false)
        assertEquals(PinReason.KidsOff, viewModel.uiState.first { it.pinPrompt != null }.pinPrompt!!.reason)
        assertEquals(true, app.profileSettings.kidsProfile.first())

        viewModel.onSubmitPin("1234")
        app.profileSettings.kidsProfile.first { it == false }
        assertNull(viewModel.uiState.first { it.pinPrompt == null }.pinPrompt)
    }

    @Test
    fun `removing the PIN asks for it first`() = runTest {
        signedInWithPin()
        app.database.accountDao().setPinOpenSettings("srv-1", "u1", false)
        val viewModel = viewModel()
        viewModel.open()

        viewModel.onAction(SettingKey.RemovePin)
        assertEquals(PinReason.RemovePin, viewModel.uiState.first { it.pinPrompt != null }.pinPrompt!!.reason)
        assertTrue(app.pins.hasPin("srv-1", "u1"))

        viewModel.onSubmitPin("1234")
        viewModel.uiState.first { it.pinPrompt == null }
        assertFalse(app.pins.hasPin("srv-1", "u1"))
    }

    @Test
    fun `five wrong PINs lock the prompt and say how long`() = runTest {
        signedInWithPin()
        val viewModel = viewModel()
        viewModel.uiState.first { it.pinPrompt != null }

        repeat(5) { viewModel.onSubmitPin("0000") }

        val locked = viewModel.uiState.first { it.pinPrompt?.lockedSeconds != null }.pinPrompt!!
        assertEquals(30, locked.lockedSeconds)
        assertEquals(Strings.pinPromptLocked(30), locked.error)
    }

    @Test
    fun `Forgot PIN with the right password removes the PIN and keeps kids mode`() = runTest {
        signedInWithPin()
        app.profileSettings.setKidsProfile(true)
        val viewModel = viewModel()
        viewModel.uiState.first { it.pinPrompt != null }

        viewModel.onForgotPin()
        val forgot = viewModel.uiState.first { it.pinPrompt?.forgot == true }.pinPrompt!!
        assertEquals(Strings.pinForgotMessage("Alex"), forgot.message)

        viewModel.onSubmitPassword("wrong")
        assertNotNull(viewModel.uiState.first { it.pinPrompt?.error != null }.pinPrompt!!.error)
        assertTrue(app.pins.hasPin("srv-1", "u1"))
        // A wrong password is not a rejected token: the session stays as it was.
        assertIs<ConnectionState.Connected>(app.connection.state.value)
        assertEquals("token-1", app.accounts.sessionToken("srv-1", "u1"))

        viewModel.onSubmitPassword("right")
        val open = viewModel.uiState.first { !it.locked }
        assertNull(open.pinPrompt)
        assertFalse(app.pins.hasPin("srv-1", "u1"))
        assertEquals(true, app.profileSettings.kidsProfile.first())
    }

    @Test
    fun `kids profiles hide Advanced and Sign out and Notifications`() = runTest {
        signedInWithPin(pin = false)
        app.profileSettings.setKidsProfile(true)
        val state = viewModel().uiState.first { it.hasProfile && it.kidsProfile }

        val root = state.page(SettingsPageId.Root).keys()
        assertFalse(SettingKey.ShowStats in root)
        assertFalse(SettingKey.About in root)
        assertFalse(SettingKey.Notifications in root)
        assertTrue(SettingKey.KidsProfile in root)
        assertFalse(SettingKey.SignOut in state.page(SettingsPageId.Profile).keys())
    }

    @Test
    fun `changing the PIN asks for the current one first`() = runTest {
        signedInWithPin()
        app.database.accountDao().setPinOpenSettings("srv-1", "u1", false)
        val viewModel = viewModel()
        viewModel.open()

        viewModel.onAction(SettingKey.ChangePin)

        assertEquals(PinReason.ChangePin, viewModel.uiState.first { it.pinPrompt != null }.pinPrompt!!.reason)
    }

    @Test
    fun `Settings opened before the profile is signed in still asks for its PIN`() = runTest {
        val viewModel = viewModel()
        assertFalse(viewModel.uiState.first { !it.locked }.hasProfile)

        app.serverAt("http://nas:8096").on("GET", "/Users/Me", Reply.json("""{"Id":"u1","Name":"Alex"}"""))
        app.accounts.saveSession("srv-1", "u1", "Alex", null, "token-1")
        app.setPin("u1", "1234")
        app.connection.activate("srv-1", "u1")

        val asked = viewModel.uiState.first { it.pinPrompt != null }
        assertEquals(PinReason.OpenSettings, asked.pinPrompt!!.reason)
        assertTrue(asked.locked)
    }
}
