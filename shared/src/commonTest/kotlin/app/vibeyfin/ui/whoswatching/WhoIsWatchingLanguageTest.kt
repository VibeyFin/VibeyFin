package app.vibeyfin.ui.whoswatching

import app.vibeyfin.domain.AuthRepository
import app.vibeyfin.domain.model.ProfileRef
import app.vibeyfin.i18n.Texts
import app.vibeyfin.testing.FakeServer
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.TestLanguages
import app.vibeyfin.testing.ViewModelTracker
import app.vibeyfin.testing.authJson
import app.vibeyfin.testing.publicUsersJson
import app.vibeyfin.testing.virtualClock
import app.vibeyfin.ui.common.Strings
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class WhoIsWatchingLanguageTest {
    private val app = TestApp()
    private val address = "http://nas:8096"
    private lateinit var server: FakeServer

    @BeforeTest
    fun setUp() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server = app.serverAt(address)
        server.on("GET", "/Users/Public", Reply.json(publicUsersJson(Triple("u1", "Alex", true), Triple("u2", "Kim", false), Triple("u3", "Sam", true))))
        server.on("GET", "/QuickConnect/Enabled", Reply.json("false"))
        server.on("POST", "/Users/AuthenticateByName") { request ->
            val name = Regex(""""Username":"([^"]*)"""").find(request.body)!!.groupValues[1]
            val password = Regex(""""Pw":"([^"]*)"""").find(request.body)!!.groupValues[1]
            val id = mapOf("alex" to "u1", "kim" to "u2", "sam" to "u3").getValue(name.lowercase())
            if ((name.lowercase() == "kim" && password == "") || password == "right") {
                Reply.json(authJson(userId = id, name = name.replaceFirstChar { it.uppercase() }, token = "token-$id"))
            } else {
                Reply.status(HttpStatusCode.Unauthorized)
            }
        }
    }

    private val viewModels = ViewModelTracker()

    @AfterTest
    fun tearDown() = runTest {
        viewModels.stopAll()
        Dispatchers.resetMain()
        app.close()
        TestLanguages.reset()
    }

    private fun TestScope.viewModel(pinFor: ProfileRef? = null): WhoIsWatchingViewModel {
        val clock = virtualClock()
        val auth = AuthRepository(app.connector, app.accounts, app.connection, clock)
        return viewModels.track(WhoIsWatchingViewModel("srv-1", app.servers, app.accounts, auth, app.connection, app.pins, clock, pinFor))
    }

    private suspend fun WhoIsWatchingViewModel.loaded() = uiState.first { !it.loading }

    @Test
    fun aSignInErrorFollowsTheLanguage() = runTest {
        TestLanguages.installGerman("sign_in_wrong_password" to "Das Passwort stimmt nicht.")
        val viewModel = viewModel()
        viewModel.onProfileTapped(viewModel.loaded().profiles.first { it.name == "Sam" })
        viewModel.onSubmitPassword("wrong")
        val before = viewModel.uiState.first { !it.signingIn && it.error != null }
        assertEquals(SignInMessages.WRONG_PASSWORD, before.error)

        Texts.select("de")

        val after = viewModel.uiState.first { it != before }
        assertEquals("Das Passwort stimmt nicht.", after.error)
    }

    @Test
    fun theQuickConnectSheetFollowsTheLanguage() = runTest {
        TestLanguages.installGerman("sign_in_quick_connect_off" to "Schnellverbindung ist ausgeschaltet.")
        server.on("POST", "/QuickConnect/Initiate", Reply.status(HttpStatusCode.Unauthorized))
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.loaded()
        viewModel.onQuickConnectTapped()
        val before = viewModel.uiState.first { (it.sheet as? SignInSheet.QuickConnect)?.message != null }
        assertEquals(SignInMessages.QUICK_CONNECT_OFF, (before.sheet as SignInSheet.QuickConnect).message)

        Texts.select("de")

        val after = viewModel.uiState.first { it != before }
        assertEquals("Schnellverbindung ist ausgeschaltet.", (after.sheet as SignInSheet.QuickConnect).message)
    }

    @Test
    fun thePinSheetFollowsTheLanguage() = runTest {
        TestLanguages.installGerman(
            "pin_prompt_open_profile" to "PIN für dieses Profil eingeben.",
            "pin_prompt_wrong" to "Falsche PIN.",
        )
        app.accounts.saveSession("srv-1", "u1", "Alex", null, "token-u1")
        app.setPin("u1", "1234")
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        val alex = viewModel.loaded().profiles.first { it.name == "Alex" }
        viewModel.onProfileTapped(alex)
        viewModel.onSubmitPin("0000")
        val before = viewModel.uiState.first { it.pinPrompt?.error != null }.pinPrompt!!
        assertEquals(Strings.pinPromptOpenProfile, before.message)
        assertEquals(Strings.pinPromptWrong, before.error)

        Texts.select("de")

        val after = viewModel.uiState.first { it.pinPrompt?.message != before.message }.pinPrompt!!
        assertEquals("PIN für dieses Profil eingeben.", after.message)
        assertEquals("Falsche PIN.", after.error)
    }
}
