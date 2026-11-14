package app.vibeyfin.ui.common

import app.vibeyfin.domain.AuthRepository
import app.vibeyfin.domain.PinLock
import app.vibeyfin.domain.PinResult
import app.vibeyfin.domain.SignInResult
import app.vibeyfin.i18n.Texts
import app.vibeyfin.ui.whoswatching.SignInMessages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Why the PIN is asked for. */
enum class PinReason {
    OpenProfile,
    OpenSettings,
    SwitchProfile,
    ChangePin,
    RemovePin,
    KidsOff,

    /** Choosing a new PIN, for Set PIN and after the current one was right for Change PIN: it is typed twice. */
    ChoosePin,
}

data class PinPrompt(
    val reason: PinReason,
    val message: String,
    val error: String? = null,
    val lockedSeconds: Int? = null,
    val forgot: Boolean = false,
    val checking: Boolean = false,
    /** Choosing a PIN: the first entry is in and the sheet asks for it again. */
    val confirming: Boolean = false,
    /** The profile whose PIN this is, for the Forgot PIN text. */
    val profileName: String = "",
)

private sealed interface PinFault {
    data object Wrong : PinFault

    data class AttemptsLeft(val attempts: Int) : PinFault

    data object Mismatch : PinFault

    data class Rejected(val result: SignInResult) : PinFault

    fun message(): String = when (this) {
        Wrong -> Strings.pinPromptWrong
        is AttemptsLeft -> Strings.pinPromptAttemptsLeft(attempts)
        Mismatch -> Strings.pinPromptMismatch
        is Rejected -> SignInMessages.forFailure(result, passwordOnly = true)
    }
}

private data class PinPromptState(
    val reason: PinReason,
    val fault: PinFault? = null,
    val lockedSeconds: Int? = null,
    val forgot: Boolean = false,
    val checking: Boolean = false,
    val confirming: Boolean = false,
    val profileName: String = "",
) {
    fun render(): PinPrompt = PinPrompt(
        reason = reason,
        message = message(),
        error = lockedSeconds?.let { Strings.pinPromptLocked(it) } ?: fault?.message(),
        lockedSeconds = lockedSeconds,
        forgot = forgot,
        checking = checking,
        confirming = confirming,
        profileName = profileName,
    )

    private fun message(): String = if (forgot) {
        Strings.pinForgotMessage(profileName)
    } else {
        when (reason) {
            PinReason.OpenProfile -> Strings.pinPromptOpenProfile
            PinReason.OpenSettings -> Strings.pinPromptOpenSettings
            PinReason.SwitchProfile -> Strings.pinPromptSwitchProfile
            PinReason.ChangePin -> Strings.pinPromptChangePin
            PinReason.RemovePin -> Strings.pinPromptRemovePin
            PinReason.KidsOff -> Strings.pinPromptKidsOff
            PinReason.ChoosePin -> if (confirming) Strings.pinPromptConfirm else Strings.pinPromptNew
        }
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
        val choosing: Boolean = false,
    )

    private val shown = MutableStateFlow<PinPrompt?>(null)
    val prompt: StateFlow<PinPrompt?> = shown.asStateFlow()

    private var pending: Pending? = null
    private var countdown: Job? = null
    private val checks = Mutex()

    // The first of the two entries while choosing a PIN.
    private var chosen: String? = null

    private var sheet: PinPromptState? = null

    init {
        scope.launch { Texts.language.collect { shown.value = sheet?.render() } }
    }

    private fun show(next: PinPromptState?) {
        sheet = next
        shown.value = next?.render()
    }

    private fun edit(change: (PinPromptState) -> PinPromptState) {
        sheet?.let { show(change(it)) }
    }

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
        chosen = null
        pending = Pending(serverId, userId, username, onPass, onDismiss)
        show(PinPromptState(reason, profileName = username))
    }

    /**
     * Opens the sheet to choose a new PIN for the signed-in profile: it takes the same four digits twice, a different
     * second entry says so and starts over, and the PIN is set once both match. Closing it sets nothing.
     */
    fun choose(serverId: String, userId: String, username: String) {
        countdown?.cancel()
        chosen = null
        pending = Pending(serverId, userId, username, onPass = {}, onDismiss = {}, choosing = true)
        show(PinPromptState(PinReason.ChoosePin, profileName = username))
    }

    fun submitPin(pin: String) {
        val asked = pending ?: return
        val prompt = sheet ?: return
        if (prompt.lockedSeconds != null || prompt.forgot) return
        scope.launch {
            // PINs typed quickly are checked in order, and one that arrives after the sheet closed or locked is dropped.
            checks.withLock {
                if (pending !== asked || sheet?.lockedSeconds != null) return@withLock
                if (asked.choosing) return@withLock choosePin(asked, pin)
                when (val result = pins.verify(asked.serverId, asked.userId, pin)) {
                    PinResult.Ok -> pass(asked)

                    is PinResult.Wrong -> edit {
                        it.copy(fault = if (result.attemptsLeft > 2) PinFault.Wrong else PinFault.AttemptsLeft(result.attemptsLeft))
                    }

                    is PinResult.Locked -> lockFor(result.secondsLeft)
                }
            }
        }
    }

    private suspend fun choosePin(asked: Pending, pin: String) {
        if (pin.length != PIN_LENGTH || !pin.all { it in '0'..'9' }) return
        val first = chosen
        when {
            first == null -> {
                chosen = pin
                edit { it.copy(confirming = true, fault = null) }
            }

            first == pin -> {
                pins.setPin(pin)
                if (pending === asked) close()
            }

            else -> {
                chosen = null
                edit { it.copy(confirming = false, fault = PinFault.Mismatch) }
            }
        }
    }

    /** "Forgot PIN?": the sheet asks for the profile's Jellyfin password instead. */
    fun forgotPin() {
        if (pending?.choosing != false) return
        edit { it.copy(forgot = true, fault = null) }
    }

    /** Checks [password] by signing in as the profile; a right one removes its PIN and opens what the PIN guarded. */
    fun submitPassword(password: String) {
        val asked = pending ?: return
        val prompt = sheet
        if (prompt == null || !prompt.forgot || prompt.checking) return
        edit { it.copy(checking = true, fault = null) }
        scope.launch {
            when (val result = auth.signIn(asked.serverId, asked.username, password)) {
                // Signing in made the profile the current one, which is the profile removePin works on.
                SignInResult.SignedIn -> {
                    pins.removePin()
                    pass(asked)
                }

                else -> edit { it.copy(checking = false, fault = PinFault.Rejected(result)) }
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
        chosen = null
        pending = null
        show(null)
    }

    private companion object {
        const val PIN_LENGTH = 4
    }

    private fun lockFor(seconds: Int) {
        countdown?.cancel()
        countdown = scope.launch {
            for (left in seconds downTo 1) {
                edit { it.copy(fault = null, lockedSeconds = left) }
                delay(1_000)
            }
            edit { it.copy(lockedSeconds = null) }
        }
    }
}
