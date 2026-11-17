package app.vibeyfin.ui.settings

import androidx.lifecycle.viewModelScope
import androidx.room3.Room
import app.vibeyfin.data.db.VibeyFinDatabase
import app.vibeyfin.data.db.buildVibeyFinDatabase
import app.vibeyfin.domain.AccountRepository
import app.vibeyfin.domain.ConnectionState
import app.vibeyfin.domain.DevicePreferences
import app.vibeyfin.domain.PinLock
import app.vibeyfin.domain.PinResult
import app.vibeyfin.domain.ProfileSettings
import app.vibeyfin.domain.SeerrConnection
import app.vibeyfin.domain.ServerRepository
import app.vibeyfin.domain.SubtitleMode
import app.vibeyfin.domain.hashPin
import app.vibeyfin.i18n.AppLanguage
import app.vibeyfin.i18n.AppLocale
import app.vibeyfin.testing.FakeDeviceInfo
import app.vibeyfin.testing.FakeServer
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.TestLanguages
import app.vibeyfin.testing.ViewModelTracker
import app.vibeyfin.testing.authJson
import app.vibeyfin.ui.common.LoadMessages
import app.vibeyfin.ui.common.PinReason
import app.vibeyfin.ui.common.Strings
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
        // Waiting reads finish first, so the ViewModels can be stopped.
        slowDatabases.forEach { it.release() }
        viewModels.stopAll()
        slowDatabases.forEach { it.database.close() }
        applicationScope.cancel()
        Dispatchers.resetMain()
        app.close()
    }

    private fun SettingsUiState.item(key: SettingKey) = pages.values.flatMap { page -> page.sections.flatMap { it.items } }.first { it.key == key }

    private fun SettingsUiState.checked(key: SettingKey) = (item(key) as ToggleItem).checked

    private fun SettingsUiState.value(key: SettingKey) = (item(key) as ChoiceItem).value

    private fun viewModel(
        preferences: DevicePreferences = app.preferences,
        profileSettings: ProfileSettings = app.profileSettings,
        pins: PinLock = app.pins,
        appVersion: String = "",
        seerr: SeerrConnection? = null,
        downloadAlerts: Boolean = true,
        appLanguages: List<AppLanguage> = emptyList(),
    ) = viewModels.track(
        SettingsViewModel(preferences, profileSettings, app.connection, app.servers, pins, app.policy, app.languages, app.auth, app.downloadRepository, applicationScope, appVersion, downloadAlerts = downloadAlerts, seerr = seerr, appLanguages = appLanguages),
    )

    @Test
    fun `the Seerr row shows where the profile stands with Seerr and leads to its page`() = runTest {
        app.signedIn()
        val viewModel = viewModel(seerr = app.seerr)

        val row = viewModel.uiState.first { it.rootKeys().contains(SettingKey.Seerr) }.item(SettingKey.Seerr) as LinkItem

        assertEquals(Strings.seerrNotSetUp, row.value)
        assertEquals(SettingsPageId.Seerr, row.target)
        assertTrue(SettingsPageId.Seerr in viewModel.uiState.value.pages)
        // Its group, Requests, sits between Notifications and Kids and PIN.
        val titles = viewModel.uiState.value.page(SettingsPageId.Root).sections.mapNotNull { it.title }
        assertEquals(listOf(Strings.notificationsTitle, Strings.requestsSection, Strings.settingsGroupKids), titles.subList(2, 5))
    }

    @Test
    fun `a kids profile has no Seerr row`() = runTest {
        app.signedIn()
        val viewModel = viewModel(seerr = app.seerr)
        viewModel.uiState.first { it.rootKeys().contains(SettingKey.Seerr) }

        app.profileSettings.setKidsProfile(true)

        assertFalse(viewModel.uiState.first { it.kidsProfile }.rootKeys().contains(SettingKey.Seerr))
        assertFalse(viewModel.uiState.first { it.kidsProfile }.page(SettingsPageId.Profile).keys().contains(SettingKey.QuickConnect))
    }

    private val twoLanguages = listOf(AppLanguage("en", "English"), AppLanguage("de", "Deutsch"))

    @Test
    fun `the language row needs a second language to be shown`() = runTest {
        app.signedIn()

        assertFalse(viewModel(appLanguages = listOf(AppLanguage("en", "English"))).uiState.first { !it.locked }.rootKeys().contains(SettingKey.AppLanguage))
        assertTrue(viewModel(appLanguages = twoLanguages).uiState.first { !it.locked }.rootKeys().contains(SettingKey.AppLanguage))
    }

    @Test
    fun `the language row lists Device language first and each language by its own name`() = runTest {
        app.signedIn()

        val row = viewModel(appLanguages = twoLanguages).uiState.first { it.rootKeys().contains(SettingKey.AppLanguage) }.item(SettingKey.AppLanguage) as ChoiceItem

        assertEquals(listOf(Strings.languageDeviceDefault, "English", "Deutsch"), row.options.map { it.label })
        assertEquals(DEVICE_LANGUAGE, row.value)
    }

    @Test
    fun `picking a language saves it and selects its row`() = runTest {
        app.signedIn()
        val viewModel = viewModel(appLanguages = twoLanguages)
        viewModel.uiState.first { it.rootKeys().contains(SettingKey.AppLanguage) }

        viewModel.onChoose(SettingKey.AppLanguage, 2)

        assertEquals("de", app.preferences.appLanguage.first())
        assertEquals(2, viewModel.uiState.first { (it.item(SettingKey.AppLanguage) as ChoiceItem).value == 2 }.value(SettingKey.AppLanguage))

        viewModel.onChoose(SettingKey.AppLanguage, DEVICE_LANGUAGE)

        assertEquals(null, app.preferences.appLanguage.first())
    }

    @Test
    fun `the pages draw again in the new language when it changes`() = runTest {
        app.signedIn()
        TestLanguages.installGerman("settings_root_title" to "Einstellungen")
        try {
            val viewModel = viewModel(appLanguages = twoLanguages)
            assertEquals(Strings.settingsRootTitle, viewModel.uiState.first { !it.locked }.page(SettingsPageId.Root).title)

            app.preferences.setAppLanguage("de")
            AppLocale(app.preferences, FakeDeviceInfo(), twoLanguages).start(applicationScope)

            assertEquals("Einstellungen", viewModel.uiState.first { it.page(SettingsPageId.Root).title == "Einstellungen" }.page(SettingsPageId.Root).title)
        } finally {
            TestLanguages.reset()
        }
    }

    @Test
    fun `without Seerr wired in the row is hidden`() = runTest {
        app.signedIn()

        assertFalse(viewModel().uiState.first { !it.locked }.rootKeys().contains(SettingKey.Seerr))
    }

    /**
     * A database whose reads wait until [release], like Room still opening on a cold start. It holds no rows, as for a profile that has
     * just signed in for the first time.
     */
    private class SlowDatabase {
        private val scheduler = TestCoroutineScheduler()
        val database: VibeyFinDatabase = Room.inMemoryDatabaseBuilder<VibeyFinDatabase>().buildVibeyFinDatabase(StandardTestDispatcher(scheduler))

        fun release() = scheduler.advanceUntilIdle()
    }

    private val slowDatabases = mutableListOf<SlowDatabase>()

    private fun slowDatabase() = SlowDatabase().also { slowDatabases += it }

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
    fun `the home hero features one title until the profile picks another number`() = runTest {
        app.signedIn()
        val viewModel = viewModel()
        assertEquals(1, viewModel.uiState.first { it.hasProfile }.value(SettingKey.HomeHeroItems))

        viewModel.onChoose(SettingKey.HomeHeroItems, 3)
        assertEquals(3, viewModel.uiState.first { it.value(SettingKey.HomeHeroItems) == 3 }.value(SettingKey.HomeHeroItems))
        viewModel.onChoose(SettingKey.HomeHeroItems, 0)
        assertEquals(0, viewModel.uiState.first { it.value(SettingKey.HomeHeroItems) == 0 }.value(SettingKey.HomeHeroItems))
    }

    @Test
    fun `switching the hero off and on again keeps its number`() = runTest {
        app.signedIn()
        app.profileSettings.setHomeHeroItems(4)
        app.profileSettings.setHomeHeroItems(0)
        assertEquals(0, app.profileSettings.homeHeroItems.first())
        // On again through the old switch's column, as a profile that had the switch off would be.
        app.profileSettings.setHomeHeroItems(4)
        assertEquals(4, app.profileSettings.homeHeroItems.first())
    }

    @Test
    fun `a hero count that is not one of the choices is ignored`() = runTest {
        app.signedIn()
        val viewModel = viewModel()
        viewModel.uiState.first { it.hasProfile }
        for (invalid in listOf(-1, 6, 10)) {
            viewModel.onChoose(SettingKey.HomeHeroItems, invalid)
            assertEquals(1, app.profileSettings.homeHeroItems.first())
        }
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
    fun `picture in picture is on by default and follows its toggle`() = runTest {
        app.signedIn()
        val viewModel = viewModel()
        assertTrue(viewModel.uiState.first { it.hasProfile }.checked(SettingKey.PictureInPicture))

        viewModel.onToggle(SettingKey.PictureInPicture, false)
        assertFalse(viewModel.uiState.first { !it.checked(SettingKey.PictureInPicture) }.checked(SettingKey.PictureInPicture))
    }

    @Test
    fun `swipe for volume and brightness is on by default and follows its toggle`() = runTest {
        app.signedIn()
        val viewModel = viewModel()
        assertTrue(viewModel.uiState.first { it.hasProfile }.checked(SettingKey.SwipeGestures))

        viewModel.onToggle(SettingKey.SwipeGestures, false)
        assertFalse(viewModel.uiState.first { !it.checked(SettingKey.SwipeGestures) }.checked(SettingKey.SwipeGestures))
        assertEquals(false, app.profileSettings.swipeGestures.first())
    }

    @Test
    fun `watched titles show in rows by default and the Home toggle hides them for this profile only`() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Users/Me", Reply.json("""{"Id":"u2","Name":"Sam"}"""))
        app.accounts.saveSession("srv-1", "u2", "Sam", null, "token-2")
        val viewModel = viewModel()
        app.connection.activate("srv-1", "u1")
        val opened = viewModel.uiState.first { it.hasProfile }
        assertTrue(opened.checked(SettingKey.ShowWatchedInRows))
        assertTrue(SettingKey.ShowWatchedInRows in opened.rootKeys())

        viewModel.onToggle(SettingKey.ShowWatchedInRows, false)
        viewModel.uiState.first { !it.checked(SettingKey.ShowWatchedInRows) }
        assertEquals(false, app.profileSettings.showWatchedInRows.first())

        app.connection.activate("srv-1", "u2")
        assertTrue(viewModel.uiState.first { it.hasProfile && it.checked(SettingKey.ShowWatchedInRows) }.checked(SettingKey.ShowWatchedInRows))
        assertEquals(true, app.profileSettings.showWatchedInRows.first())

        app.connection.activate("srv-1", "u1")
        viewModel.uiState.first { !it.checked(SettingKey.ShowWatchedInRows) }
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
    fun `the root has its groups in the designed order`() = runTest {
        app.signedIn()
        val root = viewModel().uiState.first { it.hasProfile }.page(SettingsPageId.Root)

        assertEquals(
            listOf(
                Strings.playbackSection,
                Strings.settingsGroupHome,
                Strings.notificationsTitle,
                Strings.settingsGroupKids,
                Strings.settingsGroupDevice,
                Strings.tabDownloads,
                Strings.settingsGroupAdvanced,
            ),
            root.sections.mapNotNull { it.title },
        )
        assertFalse(root.keys().contains(SettingKey.QuickConnect))
    }

    @Test
    fun `the root Playback group links to Quality and Audio and subtitles and Skipping only`() = runTest {
        app.signedIn()
        val root = viewModel().uiState.first { it.hasProfile }.page(SettingsPageId.Root)

        val playback = root.sections.single { it.title == Strings.playbackSection }
        assertTrue(playback.items.all { it is LinkItem })
        assertEquals(
            listOf(SettingKey.Quality, SettingKey.AudioSubtitles, SettingKey.Skipping),
            playback.items.map { it.key },
        )
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
            listOf(SettingKey.SkipBack, SettingKey.SkipForward, SettingKey.AutoSkip, SettingKey.BingeThreshold, SettingKey.PictureInPicture, SettingKey.SwipeGestures, SettingKey.BufferAhead),
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
        assertEquals(emptyList(), signedOut.keys() - setOf(SettingKey.AlwaysAsk, SettingKey.OfflineMode, SettingKey.About))

        app.signedIn()
        val signedIn = viewModel().uiState.first { it.hasProfile }.page(SettingsPageId.Root)
        assertTrue(SettingKey.AlwaysAsk in signedIn.keys())
        assertTrue(SettingKey.ShowStats in signedIn.keys())
        assertTrue(SettingKey.HomeHeroItems in signedIn.keys())
        assertTrue(SettingKey.DownloadOverMobile in signedIn.keys())
        assertTrue(SettingKey.StorageLimit in signedIn.keys())
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
        assertEquals(SettingsPageId.QuickConnect, (items.getValue(SettingKey.QuickConnect) as LinkItem).target)
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
    fun `a stale Sign out tap on a kids profile asks for nothing`() = runTest {
        app.signedIn()
        app.profileSettings.setKidsProfile(true)
        val viewModel = viewModel()
        viewModel.uiState.first { it.hasProfile && it.kidsProfile }

        viewModel.onAction(SettingKey.SignOut)

        assertFalse(viewModel.uiState.first().confirmSignOut)
        assertIs<ConnectionState.Connected>(app.connection.state.value)
    }

    @Test
    fun `signing out asks to confirm and then clears the session and the profile's downloads`() = runTest {
        val server = app.signedIn()
        app.files.open("srv-1/u1/m1/video.mkv", append = false).also { it.write("x".encodeToByteArray(), 1) }.close()
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
        withContext(Dispatchers.Default) { withTimeout(5_000) { while (app.files.contents.isNotEmpty()) delay(10) } }
    }

    @Test
    fun `purging downloads asks to confirm and then deletes the profile's files`() = runTest {
        app.signedIn()
        app.files.open("srv-1/u1/stray/images/poster.img", append = false).also { it.write("x".encodeToByteArray(), 1) }.close()
        val viewModel = viewModel()
        assertTrue(viewModel.uiState.first { it.hasProfile }.pages.values.flatMap { it.sections }.flatMap { it.items }.any { it.key == SettingKey.PurgeDownloads })

        viewModel.onAction(SettingKey.PurgeDownloads)
        assertTrue(viewModel.uiState.first { it.confirmPurgeDownloads }.confirmPurgeDownloads)
        assertTrue(app.files.contents.isNotEmpty(), "nothing is deleted before the confirmation")

        viewModel.onConfirmPurgeDownloads()
        assertFalse(viewModel.uiState.first { !it.confirmPurgeDownloads }.confirmPurgeDownloads)
        withContext(Dispatchers.Default) { withTimeout(5_000) { while (app.files.contents.isNotEmpty()) delay(10) } }
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
        assertFalse(viewModel.uiState.first { it.hasProfile }.disconnected)

        viewModel.onAction(SettingKey.SwitchProfile)

        assertEquals(ConnectionState.Disconnected, app.connection.state.value)
        assertTrue(viewModel.uiState.first { it.disconnected }.disconnected)
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

        val system = viewModel.uiState.first { it.hasProfile }.page(SettingsPageId.AudioSubtitles).keys()
        assertTrue(SettingKey.CaptionsFollowSystem in system)
        assertTrue(SettingKey.SubtitlePreview in system)
        assertTrue(system.none { it in styleKeys })

        viewModel.onToggle(SettingKey.CaptionsFollowSystem, false)
        val custom = viewModel.uiState.first { !it.page(SettingsPageId.AudioSubtitles).keys().none { k -> k in styleKeys } }
            .page(SettingsPageId.AudioSubtitles).keys()
        assertTrue(custom.containsAll(styleKeys))
    }

    @Test
    fun `the subtitle style follows the languages with its preview as a titled section of its own before the toggle`() = runTest {
        app.signedIn()
        val page = viewModel().uiState.first { it.hasProfile }.page(SettingsPageId.AudioSubtitles).visibleOnly()
        val preview = page.sections.indexOfFirst { s -> s.items.any { it.key == SettingKey.SubtitlePreview } }
        assertTrue(preview > page.sections.indexOfFirst { s -> s.items.any { it.key == SettingKey.RememberTracks } })
        assertEquals(listOf(SettingKey.SubtitlePreview), page.sections[preview].items.map { it.key })
        assertEquals(Strings.subtitleStyleGroup, page.sections[preview].title)
        assertEquals(SettingKey.CaptionsFollowSystem, page.sections[preview + 1].items.first().key)
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

    // The root page links to Notifications with the same key, so the toggle is read from its own page.
    private fun SettingsUiState.notificationsOn() = (pages.getValue(SettingsPageId.Notifications).sections.flatMap { it.items }.first { it.key == SettingKey.Notifications } as ToggleItem).checked

    private fun SettingsUiState.scopeRowShown() = pages.getValue(SettingsPageId.Notifications).visibleOnly().sections.flatMap { it.items }.any { it.key == SettingKey.NotificationScope }

    @Test
    fun `switching notifications on asks for permission once and saves the setting`() = runTest {
        app.signedIn()
        val viewModel = viewModel()
        assertFalse(viewModel.uiState.first { it.hasProfile }.requestNotificationPermission)

        viewModel.onToggle(SettingKey.Notifications, true)

        assertTrue(viewModel.uiState.first { it.requestNotificationPermission }.notificationsOn())
        assertTrue(app.profileSettings.notificationsEnabled.first() == true)
        viewModel.onNotificationPermissionRequested()
        assertFalse(viewModel.uiState.first { !it.requestNotificationPermission }.requestNotificationPermission)
        assertTrue(viewModel.uiState.first().notificationsOn())
    }

    @Test
    fun `download notifications start on and the toggle saves for the profile`() = runTest {
        app.signedIn()
        val viewModel = viewModel()
        assertTrue(viewModel.uiState.first { it.hasProfile }.checked(SettingKey.DownloadNotifications))

        viewModel.onToggle(SettingKey.DownloadNotifications, false)

        assertFalse(viewModel.uiState.first { !it.checked(SettingKey.DownloadNotifications) }.checked(SettingKey.DownloadNotifications))
        assertEquals(false, app.profileSettings.downloadNotifications.first())
        assertFalse(viewModel.uiState.first().requestNotificationPermission, "the permission is asked on the first download, not here")
    }

    @Test
    fun `a platform without download alerts shows no such row`() = runTest {
        app.signedIn()
        val viewModel = viewModel(downloadAlerts = false)

        val shown = viewModel.uiState.first { it.hasProfile }.pages.getValue(SettingsPageId.Notifications).visibleOnly().sections.flatMap { it.items }

        assertTrue(shown.none { it.key == SettingKey.DownloadNotifications })
    }

    @Test
    fun `the scope choice is saved`() = runTest {
        app.signedIn()
        val viewModel = viewModel()
        viewModel.uiState.first { it.hasProfile }

        viewModel.onChoose(SettingKey.NotificationScope, ProfileSettings.NOTIFY_EVERYTHING)

        assertEquals(ProfileSettings.NOTIFY_EVERYTHING, viewModel.uiState.first { it.value(SettingKey.NotificationScope) == ProfileSettings.NOTIFY_EVERYTHING }.value(SettingKey.NotificationScope))
        assertEquals(ProfileSettings.NOTIFY_EVERYTHING, app.profileSettings.notificationScope.first())
    }

    @Test
    fun `the notification scope row shows only while notifications are on`() = runTest {
        app.signedIn()
        val viewModel = viewModel()
        assertFalse(viewModel.uiState.first { it.hasProfile }.scopeRowShown())

        viewModel.onToggle(SettingKey.Notifications, true)
        assertTrue(viewModel.uiState.first { it.notificationsOn() }.scopeRowShown())

        viewModel.onToggle(SettingKey.Notifications, false)
        assertFalse(viewModel.uiState.first { !it.notificationsOn() }.scopeRowShown())
    }

    @Test
    fun `the Notifications page and its link are hidden for a kids profile`() = runTest {
        app.signedIn()
        app.profileSettings.setKidsProfile(true)
        val viewModel = viewModel()
        val state = viewModel.uiState.first { it.hasProfile && it.kidsProfile }

        assertTrue(state.pages.getValue(SettingsPageId.Notifications).visibleOnly().sections.isEmpty())
        assertFalse(state.pages.getValue(SettingsPageId.Root).visibleOnly().sections.flatMap { it.items }.any { it.key == SettingKey.Notifications })

        viewModel.onToggle(SettingKey.Notifications, true)
        assertEquals(false, app.profileSettings.notificationsEnabled.first())
        assertFalse(viewModel.uiState.first().requestNotificationPermission)
    }

    @Test
    fun `permission denied switches notifications back off and says why`() = runTest {
        app.signedIn()
        val viewModel = viewModel()
        viewModel.uiState.first { it.hasProfile }
        viewModel.onToggle(SettingKey.Notifications, true)
        viewModel.uiState.first { it.requestNotificationPermission }

        viewModel.onNotificationPermissionDenied()

        val state = viewModel.uiState.first { !it.notificationsOn() }
        assertFalse(state.requestNotificationPermission)
        assertEquals(false, app.profileSettings.notificationsEnabled.first())
        assertEquals(Strings.notificationsBlocked, state.pages.getValue(SettingsPageId.Notifications).sections.first().footer)
    }

    // Signed in as Alex, with "1234" as the PIN when [pin] is true; the switches are on unless turned off.
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

    private fun SettingsUiState.kidsPage() = pages.getValue(SettingsPageId.KidsAndPin).visibleOnly()

    private fun SettingsUiState.kidsKeys() = kidsPage().sections.flatMap { it.items }.map { it.key }

    private suspend fun SettingsViewModel.promptWith(reason: PinReason) = uiState.first { it.pinPrompt?.reason == reason }.pinPrompt!!

    @Test
    fun `setting a PIN needs the same four digits twice`() = runTest {
        signedInWithPin(pin = false)
        val viewModel = viewModel()
        viewModel.open()

        viewModel.onAction(SettingKey.SetPin)
        assertEquals(Strings.pinPromptNew, viewModel.promptWith(PinReason.ChoosePin).message)

        viewModel.onSubmitPin("1234")
        assertEquals(Strings.pinPromptConfirm, viewModel.uiState.first { it.pinPrompt?.confirming == true }.pinPrompt!!.message)

        // A different second entry says so and starts over at the first step.
        viewModel.onSubmitPin("4321")
        val restarted = viewModel.uiState.first { it.pinPrompt?.error != null }.pinPrompt!!
        assertEquals(Strings.pinPromptMismatch, restarted.error)
        assertFalse(restarted.confirming)
        assertFalse(app.pins.hasPin("srv-1", "u1"))

        viewModel.onSubmitPin("1234")
        viewModel.uiState.first { it.pinPrompt?.confirming == true }
        viewModel.onSubmitPin("1234")
        viewModel.uiState.first { it.pinPrompt == null }

        assertTrue(app.pins.hasPin("srv-1", "u1"))
        assertEquals(PinResult.Ok, app.pins.verify("srv-1", "u1", "1234"))
        assertTrue(viewModel.uiState.first { it.item(SettingKey.ChangePin).visible }.item(SettingKey.ChangePin).visible)
    }

    @Test
    fun `changing the PIN checks the current one and then chooses a new one twice`() = runTest {
        signedInWithPin()
        app.database.accountDao().setPinOpenSettings("srv-1", "u1", false)
        val viewModel = viewModel()
        viewModel.open()

        viewModel.onAction(SettingKey.ChangePin)
        viewModel.promptWith(PinReason.ChangePin)
        viewModel.onSubmitPin("1234")
        viewModel.promptWith(PinReason.ChoosePin)
        viewModel.onSubmitPin("5678")
        viewModel.uiState.first { it.pinPrompt?.confirming == true }
        viewModel.onSubmitPin("5678")
        viewModel.uiState.first { it.pinPrompt == null }

        assertEquals(PinResult.Ok, app.pins.verify("srv-1", "u1", "5678"))
        assertIs<PinResult.Wrong>(app.pins.verify("srv-1", "u1", "1234"))
    }

    @Test
    fun `dismissing the new PIN sheet sets nothing`() = runTest {
        signedInWithPin(pin = false)
        val viewModel = viewModel()
        viewModel.open()

        viewModel.onAction(SettingKey.SetPin)
        viewModel.promptWith(PinReason.ChoosePin)
        viewModel.onSubmitPin("1234")
        viewModel.onDismissPin()

        assertNull(viewModel.uiState.value.pinPrompt)
        assertFalse(app.pins.hasPin("srv-1", "u1"))
    }

    @Test
    fun `the gate switches appear only once a PIN exists`() = runTest {
        signedInWithPin(pin = false)
        val viewModel = viewModel()
        val without = viewModel.open().kidsKeys()

        assertTrue(SettingKey.KidsProfile in without)
        assertTrue(SettingKey.SetPin in without)
        listOf(SettingKey.ChangePin, SettingKey.RemovePin, SettingKey.PinOpenProfile, SettingKey.PinOpenSettings, SettingKey.PinSwitchProfile)
            .forEach { assertFalse(it in without, "$it") }

        app.setPin("u1", "1234")
        // The page waits for the PIN to open Settings, because the switches default to on.
        val with = viewModel.uiState.first { SettingKey.ChangePin in it.kidsKeys() }.kidsKeys()
        assertFalse(SettingKey.SetPin in with)
        listOf(SettingKey.ChangePin, SettingKey.RemovePin, SettingKey.PinOpenProfile, SettingKey.PinOpenSettings, SettingKey.PinSwitchProfile)
            .forEach { assertTrue(it in with, "$it") }
        assertTrue(viewModel.uiState.value.checked(SettingKey.PinOpenSettings))
    }

    @Test
    fun `a gate switch is stored`() = runTest {
        signedInWithPin()
        app.database.accountDao().setPinOpenSettings("srv-1", "u1", false)
        val viewModel = viewModel()
        viewModel.open()

        viewModel.onToggle(SettingKey.PinSwitchProfile, false)

        assertFalse(viewModel.uiState.first { !it.checked(SettingKey.PinSwitchProfile) }.checked(SettingKey.PinSwitchProfile))
        assertFalse(app.pins.gates("srv-1", "u1").switchProfile)
    }

    private fun SettingsUiState.infoValue(key: SettingKey) = (item(key) as InfoItem).value

    @Test
    fun `the server limits show read only and a failed load says so`() = runTest {
        val server = signedInWithPin(pin = false)
        server.on(
            "GET",
            "/Users/Me",
            Reply.json(
                """{"Id":"u1","Name":"Alex","Policy":{"MaxParentalRating":10,"BlockedTags":["horror","gore"],
                |"AccessSchedules":[{"DayOfWeek":"Weekday","StartHour":8.0,"EndHour":20.5},
                |{"DayOfWeek":"Saturday","StartHour":22.0,"EndHour":6.0}]}}
                """.trimMargin(),
            ),
        )
        server.on("GET", "/Localization/ParentalRatings", Reply.json("""[{"Name":"PG","Value":10}]"""))
        val viewModel = viewModel()
        viewModel.open()

        // Until the page opens there is nothing to show but the loading line.
        assertEquals(Strings.serverPolicyLoading, viewModel.uiState.value.item(SettingKey.PolicyStatus).title)
        viewModel.onPageOpened(SettingsPageId.KidsAndPin)
        val limits = viewModel.uiState.first { SettingKey.RatingLimit in it.kidsKeys() }

        assertEquals("PG", limits.infoValue(SettingKey.RatingLimit))
        assertEquals("horror, gore", limits.infoValue(SettingKey.BlockedTags))
        assertEquals("Weekdays 8:00 AM to 8:30 PM, Saturdays 10:00 PM to 6:00 AM the next day", limits.infoValue(SettingKey.AllowedHours))
        assertEquals(Strings.serverPolicyGroup, limits.kidsPage().sections.first { SettingKey.RatingLimit in it.items.map { i -> i.key } }.title)
        assertFalse(SettingKey.PolicyStatus in limits.kidsKeys())

        // Rows with no value say so, and a rating the server has no name for shows its level.
        server.on("GET", "/Users/Me", Reply.json("""{"Id":"u1","Name":"Alex","Policy":{"MaxParentalRating":0,"BlockedTags":[]}}"""))
        server.on("GET", "/Localization/ParentalRatings", Reply.status(HttpStatusCode.InternalServerError))
        viewModel.onPageOpened(SettingsPageId.KidsAndPin)
        val level = viewModel.uiState.first { it.kidsKeys().contains(SettingKey.RatingLimit) && it.infoValue(SettingKey.RatingLimit) != "PG" }
        assertEquals(Strings.serverPolicyLevel(0), level.infoValue(SettingKey.RatingLimit))
        assertEquals(Strings.serverPolicyNoTags, level.infoValue(SettingKey.BlockedTags))
        assertEquals(Strings.serverPolicyAnyTime, level.infoValue(SettingKey.AllowedHours))

        // No limits at all says so.
        server.on("GET", "/Users/Me", Reply.json("""{"Id":"u1","Name":"Alex","Policy":{}}"""))
        viewModel.onPageOpened(SettingsPageId.KidsAndPin)
        val none = viewModel.uiState.first { it.kidsKeys().contains(SettingKey.PolicyStatus) && it.item(SettingKey.PolicyStatus).title == Strings.serverPolicyNone }
        assertEquals(Strings.serverPolicyNone, none.item(SettingKey.PolicyStatus).title)

        // A failed load says so instead of showing no limits.
        server.on("GET", "/Users/Me", Reply.status(HttpStatusCode.InternalServerError))
        viewModel.onPageOpened(SettingsPageId.KidsAndPin)
        val failed = viewModel.uiState.first { it.kidsKeys().contains(SettingKey.PolicyStatus) && it.item(SettingKey.PolicyStatus).title == Strings.serverPolicyFailed }
        assertNotNull(failed.item(SettingKey.PolicyStatus).help)
        assertFalse(SettingKey.RatingLimit in failed.kidsKeys())
    }

    @Test
    fun `a PIN that is not four digits is ignored while choosing`() = runTest {
        signedInWithPin(pin = false)
        val viewModel = viewModel()
        viewModel.open()
        viewModel.onAction(SettingKey.SetPin)
        viewModel.promptWith(PinReason.ChoosePin)

        viewModel.onSubmitPin("12")
        viewModel.onSubmitPin("12a4")
        // The checks run one after another; a valid entry after them proves they were consumed.
        viewModel.onSubmitPin("1234")
        viewModel.uiState.first { it.pinPrompt?.confirming == true }
        viewModel.onSubmitPin("1234")
        viewModel.uiState.first { it.pinPrompt == null }

        assertEquals(PinResult.Ok, app.pins.verify("srv-1", "u1", "1234"))
        assertIs<PinResult.Wrong>(app.pins.verify("srv-1", "u1", "12a4"))
    }

    @Test
    fun `the limits of the previous profile are dropped when the profile changes`() = runTest {
        val server = signedInWithPin(pin = false)
        server.on("GET", "/Users/Me", Reply.json("""{"Id":"u1","Name":"Alex","Policy":{"BlockedTags":["horror"]}}"""))
        val viewModel = viewModel()
        viewModel.open()
        viewModel.onPageOpened(SettingsPageId.KidsAndPin)
        viewModel.uiState.first { SettingKey.BlockedTags in it.kidsKeys() }

        server.on("GET", "/Users/Me", Reply.json("""{"Id":"u2","Name":"Sam"}"""))
        app.accounts.saveSession("srv-1", "u2", "Sam", null, "token-2")
        app.connection.activate("srv-1", "u2")

        val state = viewModel.uiState.first { it.item(SettingKey.PolicyStatus).title == Strings.serverPolicyLoading }
        assertFalse(SettingKey.BlockedTags in state.kidsKeys())
    }

    @Test
    fun `the kids toggle works without a PIN and independently of it and a kids profile still sees the page`() = runTest {
        signedInWithPin(pin = false)
        val viewModel = viewModel()
        assertTrue(SettingKey.KidsProfile in viewModel.open().kidsKeys())

        viewModel.onToggle(SettingKey.KidsProfile, true)
        val kids = viewModel.uiState.first { it.kidsProfile }
        assertTrue((kids.kidsPage().sections.flatMap { it.items }.first { it.key == SettingKey.KidsProfile } as ToggleItem).checked)
        assertFalse(app.pins.hasPin("srv-1", "u1"))
        assertTrue(SettingKey.SetPin in kids.kidsKeys())
        val root = kids.pages.getValue(SettingsPageId.Root).visibleOnly().sections.flatMap { it.items }
        assertTrue(root.any { it is LinkItem && it.target == SettingsPageId.KidsAndPin })

        // Setting a PIN leaves the flag alone.
        app.setPin("u1", "1234")
        assertTrue(viewModel.uiState.first { SettingKey.ChangePin in it.kidsKeys() }.kidsProfile)
    }

    private fun SettingsUiState.rootKeys() = pages.getValue(SettingsPageId.Root).visibleOnly().sections.flatMap { it.items }.map { it.key }

    @Test
    fun `a signed-in profile has its pages from the moment the ViewModel exists`() = runTest {
        app.signedIn()
        val slow = slowDatabase()

        val state = viewModel(profileSettings = ProfileSettings(slow.database.accountDao(), app.connection)).uiState.value

        assertTrue(state.hasProfile)
        assertTrue(SettingKey.Quality in state.rootKeys())
        assertTrue(SettingKey.ProfileCard in state.items(SettingsPageId.Profile).map { it.key })
    }

    @Test
    fun `Settings opens with default values while the profile's settings are still being read`() = runTest {
        app.signedIn()
        val slow = slowDatabase()
        val viewModel = viewModel(profileSettings = ProfileSettings(slow.database.accountDao(), app.connection))

        val open = withTimeout(1_000) { viewModel.uiState.first { !it.locked } }
        assertTrue(open.hasProfile)
        assertEquals(3, open.value(SettingKey.BingeThreshold))
        assertEquals(10, open.value(SettingKey.SkipBack))
        assertTrue(SettingKey.HomeHeroItems in open.rootKeys())
    }

    @Test
    fun `a device setting that is slow to load does not hold back the page`() = runTest {
        app.signedIn()
        val stuck = object : DevicePreferences by app.preferences {
            override val alwaysAskWhoIsWatching: Flow<Boolean> = flow { awaitCancellation() }
        }

        val open = withTimeout(1_000) { viewModel(preferences = stuck).uiState.first { !it.locked } }
        assertTrue(open.hasProfile)
        assertFalse(open.checked(SettingKey.AlwaysAsk))
    }

    @Test
    fun `rows a kids profile must not see stay hidden until the kids flag is read`() = runTest {
        app.signedIn()
        val slow = slowDatabase()
        val viewModel = viewModel(profileSettings = ProfileSettings(slow.database.accountDao(), app.connection))

        val reading = withTimeout(1_000) { viewModel.uiState.first { !it.locked } }
        assertFalse(SettingKey.SignOut in reading.items(SettingsPageId.Profile).map { it.key })
        assertFalse(SettingKey.Notifications in reading.rootKeys())
        assertFalse(SettingKey.ShowStats in reading.rootKeys())

        slow.release()
        val read = viewModel.uiState.first { SettingKey.SignOut in it.items(SettingsPageId.Profile).map { i -> i.key } }
        assertTrue(SettingKey.Notifications in read.rootKeys())
    }

    @Test
    fun `Settings stays locked while a newly signed-in profile's PIN gate is read`() = runTest {
        val slow = slowDatabase()
        val viewModel = viewModel(pins = PinLock(slow.database.accountDao(), app.connection, app.clock))
        assertFalse(viewModel.uiState.first { !it.locked }.hasProfile)

        app.signedIn()
        assertTrue(viewModel.uiState.first { it.hasProfile }.locked)

        slow.release()
        assertNull(viewModel.uiState.first { !it.locked }.pinPrompt)
    }

    @Test
    fun `the app version shows with nobody signed in`() = runTest {
        assertTrue(SettingKey.About in viewModel(appVersion = "1.0").uiState.first { !it.locked }.rootKeys())
    }

    // Runs [block] against a [SlowDatabase], letting its reads through until the block is done.
    private suspend fun SlowDatabase.now(block: suspend () -> Unit) {
        // Outside the test's scheduler: a read on the slow database's own scheduler from inside it is refused.
        val job = CoroutineScope(Dispatchers.Unconfined).launch { block() }
        while (job.isActive) {
            release()
            yield()
        }
    }

    // Saves [userId] in the slow database too, with a PIN when [pin] is given (every gate on, the default).
    private suspend fun SlowDatabase.saveProfile(userId: String, name: String, pin: String? = null) = now {
        ServerRepository(database.serverDao(), database.accountDao(), app.vault, app.clock).save("srv-1", "Home", "10.10.7", "http://nas:8096")
        AccountRepository(database.accountDao(), app.vault, app.clock).saveSession("srv-1", userId, name, null, "token-$userId")
        if (pin != null) database.accountDao().setPin("srv-1", userId, hashPin(ByteArray(16) { it.toByte() }, pin), ByteArray(16) { it.toByte() })
    }

    // Lets the slow database's reads through a few rounds, for work that reads more than once.
    private suspend fun SlowDatabase.settle() = repeat(5) {
        release()
        yield()
    }

    private fun TestScope.emissions(viewModel: SettingsViewModel): MutableList<SettingsUiState> {
        val seen = mutableListOf<SettingsUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect { seen += it } }
        return seen
    }

    private fun SettingsUiState.showsContent() = hasProfile && !locked

    @Test
    fun `no emission shows a gated profile's settings before its PIN is entered`() = runTest {
        app.signedIn()
        val slow = slowDatabase()
        slow.saveProfile("u1", "Alex", pin = "1234")
        val viewModel = viewModel(
            profileSettings = ProfileSettings(slow.database.accountDao(), app.connection),
            pins = PinLock(slow.database.accountDao(), app.connection, app.clock),
        )
        val seen = emissions(viewModel)
        assertTrue(viewModel.uiState.value.locked)

        slow.settle()
        val asked = viewModel.uiState.first { it.pinPrompt != null }
        assertEquals(PinReason.OpenSettings, asked.pinPrompt!!.reason)
        assertTrue(asked.locked)
        val beforePin = seen.size

        viewModel.onSubmitPin("1234")
        slow.settle()
        viewModel.uiState.first { it.showsContent() }
        assertTrue(seen.take(beforePin).none { it.showsContent() })
    }

    @Test
    fun `a gated profile signed in after another one stays locked until its own PIN`() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Users/Me") { Reply.json("""{"Id":"u2","Name":"Sam"}""") }
        app.accounts.saveSession("srv-1", "u2", "Sam", null, "token-2")
        val slow = slowDatabase()
        slow.saveProfile("u1", "Alex")
        slow.saveProfile("u2", "Sam", pin = "1234")
        val viewModel = viewModel(
            profileSettings = ProfileSettings(slow.database.accountDao(), app.connection),
            pins = PinLock(slow.database.accountDao(), app.connection, app.clock),
        )
        val seen = emissions(viewModel)
        slow.settle()
        assertEquals("Alex", viewModel.uiState.first { it.showsContent() }.items(SettingsPageId.Profile).first().title)

        app.connection.switchProfile()
        app.connection.activate("srv-1", "u2")
        val samFrom = seen.size
        slow.settle()
        assertEquals(PinReason.OpenSettings, viewModel.uiState.first { it.pinPrompt != null }.pinPrompt!!.reason)
        val beforePin = seen.size

        viewModel.onSubmitPin("1234")
        slow.settle()
        viewModel.uiState.first { it.showsContent() }
        assertTrue(seen.subList(samFrom, beforePin).none { it.showsContent() })
    }

    @Test
    fun `a direct change of profile shows none of the previous profile's values`() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Users/Me") { Reply.json("""{"Id":"u2","Name":"Sam"}""") }
        app.accounts.saveSession("srv-1", "u2", "Sam", null, "token-2")
        val slow = slowDatabase()
        slow.saveProfile("u1", "Alex")
        slow.saveProfile("u2", "Sam")
        slow.now { slow.database.accountDao().setBingeThreshold("srv-1", "u1", 5) }
        val viewModel = viewModel(profileSettings = ProfileSettings(slow.database.accountDao(), app.connection))
        val seen = emissions(viewModel)
        slow.settle()
        val alex = viewModel.uiState.first { it.showsContent() && it.value(SettingKey.BingeThreshold) == 5 }
        assertTrue(SettingKey.SignOut in alex.items(SettingsPageId.Profile).map { it.key })

        val samFrom = seen.size
        app.connection.activate("srv-1", "u2")
        val sam = seen.drop(samFrom).filter { it.items(SettingsPageId.Profile).firstOrNull()?.title == "Sam" }
        assertTrue(sam.isNotEmpty())
        assertTrue(sam.none { it.value(SettingKey.BingeThreshold) == 5 })
        assertTrue(sam.none { SettingKey.SignOut in it.items(SettingsPageId.Profile).map { i -> i.key } })
    }

    @Test
    fun theOfflineModeSwitchIsKeptOnTheDevice() = runTest {
        val viewModel = viewModel()
        assertFalse(viewModel.uiState.first().checked(SettingKey.OfflineMode))

        viewModel.onToggle(SettingKey.OfflineMode, true)

        assertTrue(viewModel.uiState.first { it.checked(SettingKey.OfflineMode) }.checked(SettingKey.OfflineMode))
        assertTrue(app.preferences.offlineMode.first())
        assertTrue(app.offline.isOffline.value)
    }

    @Test
    fun mobileDataAndTheStorageLimitBelongToTheProfile() = runTest {
        app.signedIn()
        val viewModel = viewModel()
        assertFalse(viewModel.uiState.first { it.hasProfile }.checked(SettingKey.DownloadOverMobile))
        assertEquals(0, viewModel.uiState.value.value(SettingKey.StorageLimit))

        viewModel.onToggle(SettingKey.DownloadOverMobile, true)
        viewModel.onChoose(SettingKey.StorageLimit, 20)

        val state = viewModel.uiState.first { it.checked(SettingKey.DownloadOverMobile) && it.value(SettingKey.StorageLimit) == 20 }
        assertEquals(20, state.value(SettingKey.StorageLimit))
        assertEquals(20_000L, app.profileSettings.storageLimitMb.first())
    }

    @Test
    fun changingTheMobileDataSettingTellsAWaitingRunToLookAgain() = runTest {
        app.signedIn()
        val viewModel = viewModel()
        var wakes = 0
        val job = launch(Dispatchers.Unconfined) { app.wake.signal.collect { wakes++ } }
        // Only a profile with something to do asks for the work; the wake always goes out.
        viewModel.onToggle(SettingKey.DownloadOverMobile, true)

        assertEquals(1, wakes)
        job.cancel()
    }

    // Audio and subtitles: the preferred languages, kept on the Jellyfin account

    private val cultures = """[{"DisplayName":"German","ThreeLetterISOLanguageName":"ger","ThreeLetterISOLanguageNames":["ger","deu"]},
        |{"DisplayName":"English","ThreeLetterISOLanguageName":"eng"},{"DisplayName":"French","ThreeLetterISOLanguageName":"fre"}]
    """.trimMargin()

    // Signs in with [configJson] as the account's configuration; writes are accepted and become the account's.
    private suspend fun signedInWithLanguages(configJson: String = "{}"): FakeServer {
        val server = app.signedIn()

        fun me(json: String) = server.on("GET", "/Users/Me", Reply.json("""{"Id":"u1","Name":"Alex","Configuration":$json}"""))
        me(configJson)
        server.on("GET", "/Localization/Cultures", Reply.json(cultures))
        server.on("POST", "/Users/Configuration") { request ->
            me(request.body)
            Reply.noContent()
        }
        return server
    }

    // The footer under the languages' last row, which says whether they are ready; the subtitle style sections come after it.
    private fun SettingsUiState.languageFooter() = pages.getValue(SettingsPageId.AudioSubtitles).sections.first { s -> s.items.any { it.key == SettingKey.RememberTracks } }.footer

    private fun SettingsUiState.languagesReady() = languageFooter() == Strings.languagesFooter

    private fun SettingsUiState.rootLanguageSummary() = pages.getValue(SettingsPageId.Root).sections.flatMap { it.items }.first { it.key == SettingKey.AudioSubtitles }.help

    private fun SettingsUiState.languageChoice(page: SettingsPageId) = pages.getValue(page).sections.flatMap { it.items }.first { it is ChoiceItem } as ChoiceItem

    private fun SettingsUiState.linkValue(key: SettingKey) = (pages.getValue(SettingsPageId.AudioSubtitles).sections.flatMap { it.items }.first { it.key == key } as LinkItem).value

    @Test
    fun `the languages are read with the profile and summed up on the root`() = runTest {
        signedInWithLanguages("""{"AudioLanguagePreference":"ger","SubtitleLanguagePreference":"eng","SubtitleMode":"Always"}""")

        val state = viewModel().uiState.first { it.languagesReady() }

        assertEquals("German \u00B7 English subtitles always", state.rootLanguageSummary())
        assertEquals("German", state.linkValue(SettingKey.AudioLanguage))
        assertEquals("English", state.linkValue(SettingKey.SubtitleLanguage))
        assertEquals(SubtitleModeAlways, state.value(SettingKey.SubtitleMode))
        assertTrue(state.pages.getValue(SettingsPageId.AudioSubtitles).sections.flatMap { it.items }.all { it.enabled })
    }

    @Test
    fun `nothing set reads as the server defaults`() = runTest {
        signedInWithLanguages()

        val state = viewModel().uiState.first { it.languagesReady() }

        assertEquals(Strings.languagesDefaultsSummary, state.rootLanguageSummary())
        assertEquals(Strings.anyLanguage, state.linkValue(SettingKey.AudioLanguage))
        assertEquals(0, state.languageChoice(SettingsPageId.AudioLanguage).value)
        assertTrue(state.checked(SettingKey.RememberTracks))
    }

    @Test
    fun `the language lists open as picker sheets and the page that links them does not`() = runTest {
        signedInWithLanguages()

        val state = viewModel().uiState.first { it.languagesReady() }

        assertTrue(state.pages.getValue(SettingsPageId.AudioLanguage).sheet)
        assertTrue(state.pages.getValue(SettingsPageId.SubtitleLanguage).sheet)
        assertFalse(state.pages.getValue(SettingsPageId.AudioSubtitles).sheet)
    }

    @Test
    fun `picking a language saves its code to the account and checks it`() = runTest {
        signedInWithLanguages()
        val viewModel = viewModel()
        val ready = viewModel.uiState.first { it.languagesReady() }
        val german = ready.languageChoice(SettingsPageId.AudioLanguage).options.first { it.label == "German" }

        viewModel.onChooseLanguage(SettingKey.AudioLanguage, german.code.orEmpty())

        val saved = viewModel.uiState.first { it.linkValue(SettingKey.AudioLanguage) == "German" && it.languagesReady() }
        assertEquals(german.value, saved.languageChoice(SettingsPageId.AudioLanguage).value)
        assertTrue(app.jellyfin.requestsTo("POST", "/Users/Configuration").last().body.contains("\"AudioLanguagePreference\":\"ger\""))

        // "Any language" clears it.
        viewModel.onChooseLanguage(SettingKey.AudioLanguage, "")
        viewModel.uiState.first { it.linkValue(SettingKey.AudioLanguage) == Strings.anyLanguage }
        assertTrue(app.jellyfin.requestsTo("POST", "/Users/Configuration").last().body.contains("\"AudioLanguagePreference\":\"\""))
    }

    @Test
    fun `picking a language clears its search and closes its list`() = runTest {
        signedInWithLanguages()
        val viewModel = viewModel()
        viewModel.uiState.first { it.languagesReady() }
        viewModel.onSearch(SettingsPageId.SubtitleLanguage, "fre")
        val french = viewModel.uiState.first { it.pages.getValue(SettingsPageId.SubtitleLanguage).search == "fre" }
            .languageChoice(SettingsPageId.SubtitleLanguage).options.single()

        viewModel.onChooseLanguage(SettingKey.SubtitleLanguage, french.code.orEmpty())

        val picked = viewModel.uiState.first { it.closePage == SettingsPageId.SubtitleLanguage }
        assertEquals("", picked.pages.getValue(SettingsPageId.SubtitleLanguage).search)
        viewModel.onPageClosed()
        assertNull(viewModel.uiState.first { it.closePage == null }.closePage)
        assertEquals("French", viewModel.uiState.first { it.linkValue(SettingKey.SubtitleLanguage) == "French" }.linkValue(SettingKey.SubtitleLanguage))
    }

    @Test
    fun `a code the server knows by its other name shows as that language`() = runTest {
        signedInWithLanguages("""{"SubtitleLanguagePreference":"deu"}""")

        val state = viewModel().uiState.first { it.languagesReady() }

        assertEquals("German", state.linkValue(SettingKey.SubtitleLanguage))
        assertEquals("German", state.languageChoice(SettingsPageId.SubtitleLanguage).let { c -> c.options.first { it.value == c.value }.label })
    }

    @Test
    fun `a code the server doesn't list is kept and shown as its code`() = runTest {
        signedInWithLanguages("""{"AudioLanguagePreference":"tlh"}""")

        val state = viewModel().uiState.first { it.languagesReady() }

        assertEquals("tlh", state.linkValue(SettingKey.AudioLanguage))
        val choice = state.languageChoice(SettingsPageId.AudioLanguage)
        assertEquals("tlh", choice.options.first { it.value == choice.value }.label)
    }

    @Test
    fun `the mode and remember toggle are saved to the account`() = runTest {
        signedInWithLanguages()
        val viewModel = viewModel()
        viewModel.uiState.first { it.languagesReady() }

        viewModel.onChoose(SettingKey.SubtitleMode, SubtitleModeSmart)
        viewModel.uiState.first { it.value(SettingKey.SubtitleMode) == SubtitleModeSmart && it.languagesReady() }
        viewModel.onToggle(SettingKey.RememberTracks, false)
        val state = viewModel.uiState.first { !it.checked(SettingKey.RememberTracks) && it.languagesReady() }

        assertEquals(Strings.subtitleModeFooter(SubtitleMode.Smart), state.pages.getValue(SettingsPageId.AudioSubtitles).sections[1].footer)
        val body = app.jellyfin.requestsTo("POST", "/Users/Configuration").last().body
        assertTrue(body.contains("\"SubtitleMode\":\"Smart\""))
        assertTrue(body.contains("\"RememberSubtitleSelections\":false"))
    }

    @Test
    fun `a failed save puts the old value back and says so`() = runTest {
        val server = signedInWithLanguages("""{"AudioLanguagePreference":"eng"}""")
        val viewModel = viewModel()
        viewModel.uiState.first { it.languagesReady() }
        server.on("POST", "/Users/Configuration", Reply.status(HttpStatusCode.Forbidden))

        viewModel.onChooseLanguage(SettingKey.AudioLanguage, "")

        val failed = viewModel.uiState.first { it.languageFooter() == LoadMessages.FORBIDDEN }
        assertEquals("English", failed.linkValue(SettingKey.AudioLanguage))
        // The rows still work after a failed save.
        assertTrue(failed.pages.getValue(SettingsPageId.AudioSubtitles).sections.flatMap { it.items }.all { it.enabled })
    }

    @Test
    fun `an unreachable server leaves the rows inert and opening the page tries again`() = runTest {
        val server = signedInWithLanguages()
        server.on("GET", "/Users/Me", Reply.status(HttpStatusCode.ServiceUnavailable))
        val viewModel = viewModel()

        val failed = viewModel.uiState.first { it.languageFooter()?.startsWith("The server answered") == true }
        val languageKeys = setOf(SettingKey.AudioLanguage, SettingKey.SubtitleLanguage, SettingKey.SubtitleMode, SettingKey.RememberTracks)
        assertTrue(failed.pages.getValue(SettingsPageId.AudioSubtitles).sections.flatMap { it.items }.filter { it.key in languageKeys }.none { it.enabled })
        assertNull(failed.rootLanguageSummary())
        // A change can't be made while the values are unknown.
        viewModel.onChoose(SettingKey.SubtitleMode, SubtitleModeSmart)
        assertTrue(app.jellyfin.requestsTo("POST", "/Users/Configuration").isEmpty())

        server.on("GET", "/Users/Me", Reply.json("""{"Id":"u1","Name":"Alex","Configuration":{"SubtitleMode":"OnlyForced"}}"""))
        viewModel.onPageOpened(SettingsPageId.AudioSubtitles)

        assertEquals(SubtitleModeOnlyForced, viewModel.uiState.first { it.languagesReady() }.value(SettingKey.SubtitleMode))
    }

    @Test
    fun `the language search filters by name or code and says when nothing matches`() = runTest {
        signedInWithLanguages()
        val viewModel = viewModel()
        viewModel.uiState.first { it.languagesReady() }

        viewModel.onSearch(SettingsPageId.AudioLanguage, "ger")
        val byName = viewModel.uiState.first { it.pages.getValue(SettingsPageId.AudioLanguage).search == "ger" }
        assertEquals(listOf("German"), byName.languageChoice(SettingsPageId.AudioLanguage).options.map { it.label })
        // The other page keeps its own search.
        assertEquals(4, byName.languageChoice(SettingsPageId.SubtitleLanguage).options.size)

        viewModel.onSearch(SettingsPageId.AudioLanguage, "FRE")
        assertEquals(
            listOf("French"),
            viewModel.uiState.first { it.pages.getValue(SettingsPageId.AudioLanguage).search == "FRE" }.languageChoice(SettingsPageId.AudioLanguage).options.map { it.label },
        )

        viewModel.onSearch(SettingsPageId.AudioLanguage, "zzz")
        val none = viewModel.uiState.first { it.pages.getValue(SettingsPageId.AudioLanguage).search == "zzz" }.page(SettingsPageId.AudioLanguage)
        assertEquals(listOf(SettingKey.NoLanguageMatch), none.keys())

        // Opening the list again starts from the whole list.
        viewModel.onPageOpened(SettingsPageId.AudioLanguage)
        assertEquals(4, viewModel.uiState.first { it.pages.getValue(SettingsPageId.AudioLanguage).search == "" }.languageChoice(SettingsPageId.AudioLanguage).options.size)
    }

    @Test
    fun `another profile's languages never show under this one`() = runTest {
        signedInWithLanguages("""{"AudioLanguagePreference":"ger"}""")
        val viewModel = viewModel()
        viewModel.uiState.first { it.languagesReady() }

        app.connection.switchProfile()

        val state = viewModel.uiState.first { !it.hasProfile }
        assertEquals(Strings.anyLanguage, state.linkValue(SettingKey.AudioLanguage))
    }
}

private val SubtitleModeAlways = SubtitleMode.Always.ordinal
private val SubtitleModeSmart = SubtitleMode.Smart.ordinal
private val SubtitleModeOnlyForced = SubtitleMode.OnlyForced.ordinal
