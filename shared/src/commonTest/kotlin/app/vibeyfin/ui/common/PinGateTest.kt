package app.vibeyfin.ui.common

import app.vibeyfin.domain.ConnectionState
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.authJson
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PinGateTest {
    private val app = TestApp()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() = runTest {
        Dispatchers.resetMain()
        app.close()
    }

    private suspend fun signedInWithPin() {
        val server = app.signedIn()
        app.setPin("u1", "1234")
        server.on("POST", "/Users/AuthenticateByName") { request ->
            if (request.body.contains(""""Pw":"right"""")) Reply.json(authJson(token = "token-2")) else Reply.status(HttpStatusCode.Unauthorized)
        }
    }

    @Test
    fun `an accepted PIN closes the sheet and runs what it guards`() = runTest {
        signedInWithPin()
        val gate = PinGate(app.pins, app.auth, backgroundScope)
        val passed = CompletableDeferred<Unit>()

        gate.ask(PinReason.OpenSettings, "srv-1", "u1", "Alex") { passed.complete(Unit) }
        assertEquals(PinReason.OpenSettings, gate.prompt.value!!.reason)
        gate.submitPin("1234")

        passed.await()
        assertNull(gate.prompt.value)
    }

    @Test
    fun `a rejected PIN keeps the sheet open with a message and runs nothing`() = runTest {
        signedInWithPin()
        val gate = PinGate(app.pins, app.auth, backgroundScope)
        var passed = 0

        gate.ask(PinReason.OpenSettings, "srv-1", "u1", "Alex") { passed++ }
        gate.submitPin("0000")

        assertEquals(Strings.pinPromptWrong, gate.prompt.first { it?.error != null }!!.error)
        assertEquals(0, passed)
    }

    @Test
    fun `forgot PIN with the right password removes the PIN and runs what it guards`() = runTest {
        signedInWithPin()
        val gate = PinGate(app.pins, app.auth, backgroundScope)
        val passed = CompletableDeferred<Unit>()

        gate.ask(PinReason.OpenSettings, "srv-1", "u1", "Alex") { passed.complete(Unit) }
        gate.forgotPin()
        assertTrue(gate.prompt.value!!.forgot)

        gate.submitPassword("wrong")
        assertNotNull(gate.prompt.first { it?.error != null }!!.error)
        assertTrue(app.pins.hasPin("srv-1", "u1"))
        assertIs<ConnectionState.Connected>(app.connection.state.value)
        assertFalse(passed.isCompleted)

        gate.submitPassword("right")
        passed.await()
        assertNull(gate.prompt.value)
        assertFalse(app.pins.hasPin("srv-1", "u1"))
    }

    @Test
    fun `dismissing the sheet runs the dismiss action and not the guarded one`() = runTest {
        signedInWithPin()
        val gate = PinGate(app.pins, app.auth, backgroundScope)
        var passed = 0
        var dismissed = 0

        gate.ask(PinReason.OpenSettings, "srv-1", "u1", "Alex", onDismiss = { dismissed++ }) { passed++ }
        gate.dismiss()

        assertNull(gate.prompt.value)
        assertEquals(1, dismissed)
        assertEquals(0, passed)
    }
}
