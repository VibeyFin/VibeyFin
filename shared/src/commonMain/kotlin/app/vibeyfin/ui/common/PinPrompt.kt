package app.vibeyfin.ui.common

import app.vibeyfin.domain.AuthRepository
import app.vibeyfin.domain.PinLock
import app.vibeyfin.domain.PinResult
import app.vibeyfin.domain.SignInResult
import app.vibeyfin.ui.whoswatching.SignInMessages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Why the PIN is asked for. */
enum class PinReason { OpenProfile, OpenSettings, SwitchProfile, ChangePin, RemovePin, KidsOff }

/**
 * The PIN sheet. [error] says what went wrong; while [lockedSeconds] is set, too many wrong PINs were entered and the
 * keypad waits that many more seconds (counting down). After "Forgot PIN?" the sheet asks for the profile's Jellyfin
 * password instead ([forgot]), and [checking] is true while the server checks it.
 */
data class PinPrompt(
    val reason: PinReason,
    val error: String? = null,
    val lockedSeconds: Int? = null,
    val forgot: Boolean = false,
    val checking: Boolean = false,
    /** The profile whose PIN this is, for the Forgot PIN text. */
    val profileName: String = "",
) {
    /** The line under the sheet's title. */
    val message: String
        get() = if (forgot) Strings.pinForgotMessage(profileName) else when (reason) {
            PinReason.OpenProfile -> Strings.pinPromptOpenProfile
            PinReason.OpenSettings -> Strings.pinPromptOpenSettings
            PinReason.SwitchProfile -> Strings.pinPromptSwitchProfile
            PinReason.ChangePin -> Strings.pinPromptChangePin
            PinReason.RemovePin -> Strings.pinPromptRemovePin
            PinReason.KidsOff -> Strings.pinPromptKidsOff
        }
}

/**
 * Asks for one profile's PIN on behalf of a view model, and runs what it guards once the PIN is right. "Forgot PIN?"
 * checks the profile's Jellyfin password with a normal sign-in (never stored); a right password removes the PIN, keeps
 * the kids flag, and counts as the right PIN. Only one question is open at a time; a new [ask] replaces it.
 */
class PinGate(
    private val pins: PinLock,
    private val auth: AuthRepository,
    private val scope: CoroutineScope,
) {
    private class Pending(
        val serverId: String,
        val userId: String,
        val username: String,
        val onPass: suspend () -> Unit,
        val onDismiss: () -> Unit,
    )

    private val _prompt = MutableStateFlow<PinPrompt?>(null)
    val prompt: StateFlow<PinPrompt?> = _prompt.asStateFlow()

    private var pending: Pending? = null
    private var countdown: Job? = null
    private val checks = Mutex()

    /** Opens the sheet for [userId]'s PIN; [onPass] runs after the right PIN (or password), [onDismiss] if it is closed. */
    fun ask(
        reason: PinReason,
        serverId: String,
        userId: String,
        username: String,
        onDismiss: () -> Unit = {},
        onPass: suspend () -> Unit,
    ) {
        countdown?.cancel()
        pending = Pending(serverId, userId, username, onPass, onDismiss)
        _prompt.value = PinPrompt(reason, profileName = username)
    }

    fun submitPin(pin: String) {
        val asked = pending ?: return
        val prompt = _prompt.value ?: return
        if (prompt.lockedSeconds != null || prompt.forgot) return
        scope.launch {
            // PINs typed quickly are checked in order, and one that arrives after the sheet closed or locked is dropped.
            checks.withLock {
                if (pending !== asked || _prompt.value?.lockedSeconds != null) return@withLock
                when (val result = pins.verify(asked.serverId, asked.userId, pin)) {
                    PinResult.Ok -> pass(asked)
                    is PinResult.Wrong -> _prompt.update {
                        it?.copy(error = if (result.attemptsLeft > 2) Strings.pinPromptWrong else Strings.pinPromptAttemptsLeft(result.attemptsLeft))
                    }
                    is PinResult.Locked -> lockFor(result.secondsLeft)
                }
            }
        }
    }

    /** "Forgot PIN?": the sheet asks for the profile's Jellyfin password instead. */
    fun forgotPin() {
        if (pending == null) return
        _prompt.update { it?.copy(forgot = true, error = null) }
    }

    /** Checks [password] by signing in as the profile; a right one removes its PIN and opens what the PIN guarded. */
    fun submitPassword(password: String) {
        val asked = pending ?: return
        val prompt = _prompt.value
        if (prompt == null || !prompt.forgot || prompt.checking) return
        _prompt.update { it?.copy(checking = true, error = null) }
        scope.launch {
            when (val result = auth.signIn(asked.serverId, asked.username, password)) {
                // Signing in made the profile the current one, which is the profile removePin works on.
                SignInResult.SignedIn -> {
                    pins.removePin()
                    pass(asked)
                }
                else -> _prompt.update { it?.copy(checking = false, error = SignInMessages.forFailure(result, passwordOnly = true)) }
            }
        }
    }

    fun dismiss() {
        val asked = pending ?: return
        close()
        asked.onDismiss()
    }

    private suspend fun pass(asked: Pending) {
        if (pending !== asked) return
        close()
        asked.onPass()
    }

    private fun close() {
        countdown?.cancel()
        pending = null
        _prompt.value = null
    }

    private fun lockFor(seconds: Int) {
        countdown?.cancel()
        countdown = scope.launch {
            for (left in seconds downTo 1) {
                _prompt.update { it?.copy(error = Strings.pinPromptLocked(left), lockedSeconds = left) }
                delay(1_000)
            }
            _prompt.update { it?.copy(error = null, lockedSeconds = null) }
        }
    }
}
