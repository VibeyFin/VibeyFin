package com.liamgleeson.vibeyfin.data.vault

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyStore

/** Runs on a device: the Android Keystore can't be faked on the JVM. */
class AndroidKeystoreTokenVaultTest {
    private val alias = "vibeyfin.token.test"
    private val vault = AndroidKeystoreTokenVault(alias)
    private val token = "0f5d9c1e7b3a4c0f8e2d6a9b1c3e5f70"

    @After
    fun deleteTestKey() {
        keyStore().deleteEntry(alias)
    }

    @Test
    fun aSealedTokenOpensToTheSameToken() {
        assertEquals(token, vault.open(vault.seal(token)))
    }

    @Test
    fun theBlobIsA12ByteIvThenCiphertextAndTag() {
        val blob = vault.seal(token)

        assertEquals(12 + token.encodeToByteArray().size + 16, blob.size)
    }

    @Test
    fun theBlobDoesNotContainThePlainToken() {
        val blob = vault.seal(token)

        assertFalse(blob.decodeToString().contains(token))
    }

    @Test
    fun sealingTheSameTokenTwiceGivesDifferentBlobs() {
        val first = vault.seal(token)
        val second = vault.seal(token)

        assertFalse(first.contentEquals(second))
        assertEquals(token, vault.open(first))
        assertEquals(token, vault.open(second))
    }

    @Test
    fun aTamperedBlobIsRejectedWhereverItIsChanged() {
        val blob = vault.seal(token)

        // First byte is in the IV, the middle is ciphertext, the last byte is in the GCM tag.
        for (index in listOf(0, blob.size / 2, blob.size - 1)) {
            val tampered = blob.copyOf().also { it[index] = (it[index].toInt() xor 0x01).toByte() }
            assertNull("flipping byte $index should be rejected", vault.open(tampered))
        }
    }

    @Test
    fun aTruncatedOrEmptyBlobIsRejected() {
        val blob = vault.seal(token)

        assertNull(vault.open(ByteArray(0)))
        assertNull(vault.open(blob.copyOf(12)))
        assertNull(vault.open(blob.copyOf(blob.size - 1)))
    }

    @Test
    fun theKeyCannotBeExported() {
        vault.seal(token)

        val key = keyStore().getKey(alias, null)

        assertNotNull(key)
        assertNull("a Keystore key must not expose its bytes", key.encoded)
        assertEquals("AES", key.algorithm)
    }

    @Test
    fun aBlobIsUnreadableOnceItsKeyIsGone() {
        val blob = vault.seal(token)

        keyStore().deleteEntry(alias)

        assertNull(vault.open(blob))
        // The vault keeps working with a fresh key.
        assertEquals(token, vault.open(vault.seal(token)))
    }

    @Test
    fun aNewVaultInstanceReadsWhatAnEarlierOneSealed() {
        val blob = vault.seal(token)

        assertEquals(token, AndroidKeystoreTokenVault(alias).open(blob))
    }

    @Test
    fun discardingDoesNotBreakTheKey() {
        val blob = vault.seal(token)

        vault.discard(blob)

        assertTrue(keyStore().containsAlias(alias))
        assertEquals(token, vault.open(blob))
    }

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
}
