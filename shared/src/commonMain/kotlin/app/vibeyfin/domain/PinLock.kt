package app.vibeyfin.domain

import app.vibeyfin.data.db.AccountDao
import app.vibeyfin.data.db.PinGateRow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** What checking a PIN came to. */
sealed interface PinResult {
    /** The PIN is right, or the profile has none. */
    data object Ok : PinResult

    /** The PIN is wrong; [attemptsLeft] more wrong ones lock it for a while. */
    data class Wrong(val attemptsLeft: Int) : PinResult

    /** Too many wrong PINs; nothing is checked, not even the right PIN, for [secondsLeft] more seconds. */
    data class Locked(val secondsLeft: Int) : PinResult
}

/**
 * When a profile asks for its PIN. Each switch is already combined with [hasPin]: a profile without a PIN asks for nothing.
 * Turning kids mode off, changing the PIN and removing it ask whenever [hasPin] is true; no switch covers them.
 */
data class PinGates(
    val hasPin: Boolean = false,
    /** Opening this profile, from its tile or when the app reopens it. */
    val openProfile: Boolean = false,
    val openSettings: Boolean = false,
    /** Leaving this profile for "Who's watching?", from inside the app or at launch. */
    val switchProfile: Boolean = false,
)

internal fun PinGateRow?.toGates(): PinGates = if (this == null || !hasPin) {
    PinGates()
} else {
    PinGates(
        hasPin = true,
        openProfile = pinOpenProfile,
        openSettings = pinOpenSettings,
        switchProfile = pinSwitchProfile,
    )
}

/**
 * A profile's PIN: set, removed and checked. Only a salted hash is stored, never the PIN. Wrong guesses are counted
 * in memory per profile; [MAX_ATTEMPTS] in a row lock that profile for [LOCK_SECONDS], and a restart clears the count.
 */
class PinLock(
    private val dao: AccountDao,
    private val connection: ConnectionManager,
    private val clock: Clock,
) {
    private class Tries(var wrong: Int = 0, var lockedUntil: Instant? = null)

    private val tries = mutableMapOf<Pair<String, String>, Tries>()

    // One check, set or removal at a time, so PINs entered quickly each count once against the lockout.
    private val mutex = Mutex()

    suspend fun hasPin(serverId: String, userId: String): Boolean = dao.getPin(serverId, userId)?.pinHash != null

    /** When [userId] on [serverId] asks for its PIN; nothing for a profile this device has never saved. */
    suspend fun gates(serverId: String, userId: String): PinGates = dao.getPinGates(serverId, userId).toGates()

    /** Sets the PIN of the profile that is signed in, with a fresh salt. Does nothing when nobody is. */
    suspend fun setPin(pin: String) {
        mutex.withLock {
            connection.asAccount(Unit) { account ->
                val salt = Random.nextBytes(SALT_BYTES)
                dao.setPin(account.serverId, account.userId, hashPin(salt, pin), salt)
                tries.remove(account.serverId to account.userId)
            }
        }
    }

    /** Removes the PIN of the profile that is signed in. Does nothing when nobody is. */
    suspend fun removePin() {
        mutex.withLock {
            connection.asAccount(Unit) { account ->
                dao.setPin(account.serverId, account.userId, null, null)
                tries.remove(account.serverId to account.userId)
            }
        }
    }

    suspend fun verify(serverId: String, userId: String, pin: String): PinResult {
        mutex.withLock {
            val stored = dao.getPin(serverId, userId)
            val hash = stored?.pinHash
            val salt = stored?.pinSalt
            if (hash == null || salt == null) return PinResult.Ok

            val state = tries.getOrPut(serverId to userId) { Tries() }
            val now = clock.now()
            state.lockedUntil?.let { until ->
                if (now < until) return PinResult.Locked(secondsLeft(until - now))
                state.lockedUntil = null
                state.wrong = 0
            }
            if (hashPin(salt, pin).contentEquals(hash)) {
                state.wrong = 0
                return PinResult.Ok
            }
            state.wrong++
            if (state.wrong >= MAX_ATTEMPTS) {
                state.lockedUntil = now + LOCK_SECONDS.seconds
                return PinResult.Locked(LOCK_SECONDS)
            }
            return PinResult.Wrong(MAX_ATTEMPTS - state.wrong)
        }
    }

    private fun secondsLeft(left: kotlin.time.Duration): Int = left.inWholeMilliseconds.let { ((it + 999) / 1000).toInt() }

    companion object {
        const val MAX_ATTEMPTS = 5
        const val LOCK_SECONDS = 30
        private const val SALT_BYTES = 16
    }
}

/** SHA-256 of the salt followed by the PIN's UTF-8 bytes. */
internal fun hashPin(salt: ByteArray, pin: String): ByteArray = sha256(salt + pin.encodeToByteArray())

private val K = intArrayOf(
    0x428a2f98, 0x71374491, -0x4a3f0431, -0x164a245b, 0x3956c25b, 0x59f111f1, -0x6dc07d5c, -0x54e3a12b,
    -0x27f85568, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, -0x7f214e02, -0x6423f959, -0x3e640e8c,
    -0x1b64963f, -0x1041b87a, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
    -0x67c1aeae, -0x57ce3993, -0x4ffcd838, -0x40a68039, -0x391ff40d, -0x2a586eb9, 0x06ca6351, 0x14292967,
    0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, -0x7e3d36d2, -0x6d8dd37b,
    -0x5d40175f, -0x57e599b5, -0x3db47490, -0x3893ae5d, -0x2e6d17e7, -0x2966f9dc, -0x0bf1ca7b, 0x106aa070,
    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
    0x748f82ee, 0x78a5636f, -0x7b3787ec, -0x7338fdf8, -0x6f410006, -0x5baf9315, -0x41065c09, -0x398e870e,
)

/** SHA-256 (FIPS 180-4) in plain Kotlin, because common code has no hashing and a PIN hash needs no more than this. */
internal fun sha256(message: ByteArray): ByteArray {
    val h = intArrayOf(
        0x6a09e667,
        -0x4498517b,
        0x3c6ef372,
        -0x5ab00ac6,
        0x510e527f,
        -0x64fa9774,
        0x1f83d9ab,
        0x5be0cd19,
    )
    val padded = message.size + 1 + (((55 - message.size) % 64) + 64) % 64 + 8
    val data = ByteArray(padded)
    message.copyInto(data)
    data[message.size] = 0x80.toByte()
    val bits = message.size.toLong() * 8
    for (i in 0 until 8) data[padded - 1 - i] = (bits ushr (8 * i)).toByte()

    val w = IntArray(64)
    for (block in 0 until padded step 64) {
        for (t in 0 until 16) {
            val o = block + t * 4
            w[t] = ((data[o].toInt() and 0xff) shl 24) or ((data[o + 1].toInt() and 0xff) shl 16) or
                ((data[o + 2].toInt() and 0xff) shl 8) or (data[o + 3].toInt() and 0xff)
        }
        for (t in 16 until 64) {
            val s0 = w[t - 15].rotr(7) xor w[t - 15].rotr(18) xor (w[t - 15] ushr 3)
            val s1 = w[t - 2].rotr(17) xor w[t - 2].rotr(19) xor (w[t - 2] ushr 10)
            w[t] = w[t - 16] + s0 + w[t - 7] + s1
        }
        var a = h[0]
        var b = h[1]
        var c = h[2]
        var d = h[3]
        var e = h[4]
        var f = h[5]
        var g = h[6]
        var hh = h[7]
        for (t in 0 until 64) {
            val t1 = hh + (e.rotr(6) xor e.rotr(11) xor e.rotr(25)) + ((e and f) xor (e.inv() and g)) + K[t] + w[t]
            val t2 = (a.rotr(2) xor a.rotr(13) xor a.rotr(22)) + ((a and b) xor (a and c) xor (b and c))
            hh = g
            g = f
            f = e
            e = d + t1
            d = c
            c = b
            b = a
            a = t1 + t2
        }
        h[0] += a
        h[1] += b
        h[2] += c
        h[3] += d
        h[4] += e
        h[5] += f
        h[6] += g
        h[7] += hh
    }
    return ByteArray(32) { (h[it / 4] ushr (24 - 8 * (it % 4))).toByte() }
}

private fun Int.rotr(n: Int): Int = (this ushr n) or (this shl (32 - n))
