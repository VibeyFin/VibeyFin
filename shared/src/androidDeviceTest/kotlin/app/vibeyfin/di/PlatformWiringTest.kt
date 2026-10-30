package app.vibeyfin.di

import androidx.test.platform.app.InstrumentationRegistry
import app.vibeyfin.data.api.ClientInfoProvider
import app.vibeyfin.data.api.authorizationHeader
import app.vibeyfin.data.db.VibeyFinDatabase
import app.vibeyfin.data.vault.AndroidKeystoreTokenVault
import app.vibeyfin.domain.AccountRepository
import app.vibeyfin.domain.DevicePreferences
import app.vibeyfin.domain.ServerRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.koin.android.ext.koin.androidContext
import org.koin.core.Koin
import org.koin.dsl.koinApplication
import java.security.KeyStore
import java.util.UUID

/** The real Android bindings: a file database, the Keystore vault and a DataStore file. */
class PlatformWiringTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val open = mutableListOf<Koin>()

    @Before
    fun setUp() = cleanUp()

    @After
    fun tearDown() = cleanUp()

    @Test
    fun aSignedInProfileSurvivesReopeningTheDatabase() = runBlocking {
        val first = start()
        first.get<ServerRepository>().save("srv", "Home", "10.10.7", "http://nas:8096")
        first.get<AccountRepository>().saveSession("srv", "u1", "Alex", "tag", "token-1")
        first.get<VibeyFinDatabase>().close()
        open.remove(first)

        val second = start()

        assertEquals(listOf("http://nas:8096"), second.get<ServerRepository>().get("srv")!!.addresses)
        assertEquals("token-1", second.get<AccountRepository>().sessionToken("srv", "u1"))
    }

    @Test
    fun theTokenOnDiskIsNotThePlainToken() = runBlocking {
        val koin = start()
        koin.get<ServerRepository>().save("srv", "Home", "10.10.7", "http://nas:8096")
        koin.get<AccountRepository>().saveSession("srv", "u1", "Alex", null, "plain-token-value")
        koin.get<VibeyFinDatabase>().close()
        open.remove(koin)

        val file = context.getDatabasePath("vibeyfin.db").readBytes()

        assertFalse(file.decodeToString().contains("plain-token-value"))
    }

    @Test
    fun aProfileIsLockedWhenItsKeystoreKeyIsGone() = runBlocking {
        val koin = start()
        koin.get<ServerRepository>().save("srv", "Home", "10.10.7", "http://nas:8096")
        val accounts = koin.get<AccountRepository>()
        accounts.saveSession("srv", "u1", "Alex", null, "token-1")

        keyStore().deleteEntry(AndroidKeystoreTokenVault.DEFAULT_KEY_ALIAS)

        assertNull(accounts.sessionToken("srv", "u1"))
        assertFalse(accounts.observeAccounts("srv").first().single().hasSession)
    }

    // DataStore allows one instance per file per process and a Koin app doesn't release it when it
    // closes, so only one test here may use it. Keep everything that needs it in this test.
    @Test
    fun theDeviceIdAndClientInfoComeFromTheDevice() = runBlocking {
        val koin = start()
        val preferences = koin.get<DevicePreferences>()

        val id = preferences.deviceId()
        val info = koin.get<ClientInfoProvider>().current()

        assertEquals(id, UUID.fromString(id).toString())
        assertEquals(id, preferences.deviceId())
        assertFalse(preferences.alwaysAskWhoIsWatching.first())
        assertEquals(id, info.deviceId)
        assertTrue(info.deviceName.isNotBlank())
        assertTrue(info.appVersion.isNotBlank())
        // OkHttp rejects non-ASCII header values, whatever the device is called.
        assertTrue(info.authorizationHeader("token").all { it.code in 32..126 })
    }

    private fun start(): Koin = koinApplication {
        androidContext(context)
        modules(platformModule(), appModule())
    }.koin.also { open += it }

    private fun cleanUp() {
        open.forEach { koin ->
            runCatching { koin.get<VibeyFinDatabase>().close() }
            runCatching { koin.close() }
        }
        open.clear()
        context.deleteDatabase("vibeyfin.db")
        context.filesDir.resolve("device.preferences_pb").delete()
        keyStore().deleteEntry(AndroidKeystoreTokenVault.DEFAULT_KEY_ALIAS)
    }

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
}
