package app.vibeyfin.data.vault

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM with a key that lives in the Android Keystore and can't be exported.
 * Blob layout: 12-byte IV, then the ciphertext with its 16-byte GCM tag.
 */
class AndroidKeystoreTokenVault(
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
) : TokenVault {
    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    override fun seal(token: String): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return cipher.iv + cipher.doFinal(token.encodeToByteArray())
    }

    override fun open(sealed: ByteArray): String? {
        if (sealed.size <= IV_SIZE + TAG_SIZE) return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_SIZE * 8, sealed, 0, IV_SIZE))
            cipher.doFinal(sealed, IV_SIZE, sealed.size - IV_SIZE).decodeToString()
        } catch (_: Exception) {
            // Bad tag (tampered), key gone or invalidated, keystore hiccup: all mean "unreadable".
            null
        }
    }

    /** The key lives in the Keystore; there is nothing to release per token. */
    override fun discard(sealed: ByteArray) = Unit

    @Synchronized
    private fun key(): SecretKey {
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        const val DEFAULT_KEY_ALIAS = "vibeyfin.token.v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
        private const val TAG_SIZE = 16
    }
}
