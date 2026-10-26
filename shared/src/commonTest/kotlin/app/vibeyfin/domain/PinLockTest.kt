package app.vibeyfin.domain

import app.vibeyfin.testing.TestApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class PinLockTest {
    private val app = TestApp()
    private val lock = PinLock(app.database.accountDao(), app.connection, app.clock)

    @AfterTest
    fun tearDown() = runTest { app.close() }

    @Test
    fun `a correct PIN verifies and a wrong one does not`() = runTest {
        app.signedIn()
        lock.setPin("1234")

        assertTrue(lock.hasPin("srv-1", "u1"))
        assertEquals(PinResult.Ok, lock.verify("srv-1", "u1", "1234"))
        assertEquals(PinResult.Wrong(attemptsLeft = 4), lock.verify("srv-1", "u1", "0000"))
        assertEquals(PinResult.Wrong(attemptsLeft = 3), lock.verify("srv-1", "u1", "1111"))
        assertEquals(PinResult.Ok, lock.verify("srv-1", "u1", "1234"))
        // A correct PIN starts the count over.
        assertEquals(PinResult.Wrong(attemptsLeft = 4), lock.verify("srv-1", "u1", "0000"))
    }

    @Test
    fun `five wrong PINs lock for 30 seconds and a correct PIN during the lock still fails`() = runTest {
        app.signedIn()
        lock.setPin("1234")

        repeat(4) { assertIs<PinResult.Wrong>(lock.verify("srv-1", "u1", "0000")) }
        assertEquals(PinResult.Locked(secondsLeft = 30), lock.verify("srv-1", "u1", "0000"))

        app.clock.advance(10.seconds)
        assertEquals(PinResult.Locked(secondsLeft = 20), lock.verify("srv-1", "u1", "1234"))

        app.clock.advance(20.seconds)
        assertEquals(PinResult.Ok, lock.verify("srv-1", "u1", "1234"))
    }

    @Test
    fun `after a lock ends a wrong PIN counts from the start again`() = runTest {
        app.signedIn()
        lock.setPin("1234")
        repeat(5) { lock.verify("srv-1", "u1", "0000") }

        app.clock.advance(30.seconds)

        assertEquals(PinResult.Wrong(attemptsLeft = 4), lock.verify("srv-1", "u1", "0000"))
    }

    @Test
    fun `the PIN is never stored in the clear`() = runTest {
        app.signedIn()
        lock.setPin("1234")

        val stored = app.database.accountDao().getPin("srv-1", "u1")!!
        val hash = assertNotNull(stored.pinHash)
        val salt = assertNotNull(stored.pinSalt)
        assertFalse(hash.contentEquals("1234".encodeToByteArray()))
        assertEquals(32, hash.size)
        assertTrue(salt.isNotEmpty())

        lock.setPin("1234")
        val again = app.database.accountDao().getPin("srv-1", "u1")!!
        assertFalse(salt.contentEquals(again.pinSalt!!), "a fresh salt each time")
    }

    @Test
    fun `a profile without a PIN verifies as Ok`() = runTest {
        app.signedIn()

        assertFalse(lock.hasPin("srv-1", "u1"))
        assertEquals(PinResult.Ok, lock.verify("srv-1", "u1", "anything"))
    }

    @Test
    fun `removing the PIN clears it`() = runTest {
        app.signedIn()
        lock.setPin("1234")

        lock.removePin()

        assertFalse(lock.hasPin("srv-1", "u1"))
        assertEquals(PinResult.Ok, lock.verify("srv-1", "u1", "0000"))
    }

    @Test
    fun `the hash is the SHA-256 of the salt then the PIN`() {
        // FIPS 180-4 test vectors: "abc", and the 56-byte two-block message.
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", sha256("abc".encodeToByteArray()).toHex())
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            sha256("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".encodeToByteArray()).toHex(),
        )
        assertContentEquals(sha256(byteArrayOf(1, 2, 0x31)), hashPin(byteArrayOf(1, 2), "1"))
    }

    private fun ByteArray.toHex() = joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    @Test
    fun `wrong PINs checked at the same time each count once`() = runTest {
        app.signedIn()
        lock.setPin("1234")

        val results = withContext(Dispatchers.Default) {
            (1..5).map { async { lock.verify("srv-1", "u1", "0000") } }.awaitAll()
        }

        assertEquals(setOf(4, 3, 2, 1), results.filterIsInstance<PinResult.Wrong>().map { it.attemptsLeft }.toSet())
        assertEquals(listOf(PinResult.Locked(secondsLeft = 30)), results.filterIsInstance<PinResult.Locked>())
    }
}
