package com.liamgleeson.vibeyfin.data.vault

/**
 * Keeps access tokens encrypted at rest. Passwords are never stored, only tokens.
 *
 * What goes into the database is an opaque blob returned by [seal]. Android encrypts the token
 * with a non-exportable Keystore key (blob = 12-byte IV + ciphertext); iOS keeps the token in the
 * Keychain and the blob is the handle to it. Callers treat the blob as a black box.
 */
interface TokenVault {
    fun seal(token: String): ByteArray

    /** Returns the token, or null when the blob can't be read (tampered, or its key is gone). */
    fun open(sealed: ByteArray): String?

    /** Releases whatever [seal] stored. Call when a blob is replaced, cleared or its row deleted. */
    fun discard(sealed: ByteArray)
}
