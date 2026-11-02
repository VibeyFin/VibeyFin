package app.vibeyfin.app.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.vibeyfin.app.components.BusyButton
import app.vibeyfin.app.components.PasswordField
import app.vibeyfin.app.components.SheetColumn
import app.vibeyfin.app.components.SheetTextButton
import app.vibeyfin.app.components.SheetTitle
import app.vibeyfin.app.components.VibeyFinSheet
import app.vibeyfin.app.components.vibeyFinTextFieldColors
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.ui.common.PinPrompt
import app.vibeyfin.ui.common.PinReason
import app.vibeyfin.ui.common.Strings

private const val PIN_LENGTH = 4

/**
 * The PIN sheet: four dots over a numeric keypad, the lock countdown, and "Forgot PIN?" leading to the profile's
 * Jellyfin password. It only draws [prompt] and forwards what is typed; the view model checks it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinSheet(
    prompt: PinPrompt,
    onSubmitPin: (String) -> Unit,
    onForgotPin: () -> Unit,
    onSubmitPassword: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    VibeyFinSheet(onDismiss) {
        SheetColumn(onCancel = onDismiss, horizontalAlignment = Alignment.CenterHorizontally) {
            val choosing = prompt.reason == PinReason.ChoosePin && !prompt.forgot

            SheetTitle(
                when {
                    prompt.forgot -> Strings.pinForgot
                    choosing -> prompt.message
                    else -> Strings.pinPromptEnter
                },
            )

            if (!choosing) {
                Text(
                    text = prompt.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            if (prompt.forgot) {
                ForgotContent(prompt, onSubmitPassword)
            } else {
                KeypadContent(prompt, onSubmitPin, onForgotPin, offerForgot = !choosing)
            }
        }
    }
}

@Composable
private fun KeypadContent(prompt: PinPrompt, onSubmitPin: (String) -> Unit, onForgotPin: () -> Unit, offerForgot: Boolean) {
    var digits by remember(prompt.reason, prompt.confirming) { mutableStateOf("") }
    val locked = prompt.lockedSeconds != null
    val busy = locked || prompt.checking

    // The lock is announced once when it starts; the countdown text below changes silently every second
    val view = LocalView.current

    LaunchedEffect(locked) { if (locked) prompt.error?.let { view.announceForAccessibility(it) } }

    val press = { digit: Char ->
        if (!busy && digits.length < PIN_LENGTH) {
            val next = digits + digit

            if (next.length == PIN_LENGTH) {
                digits = ""

                onSubmitPin(next)
            } else {
                digits = next
            }
        }
    }

    PinDotsRow(filled = digits.length)

    // A wrong PIN or the lock countdown is announced as it appears
    Text(
        text = prompt.error.orEmpty(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().then(if (locked) Modifier else Modifier.semantics { liveRegion = LiveRegionMode.Polite }),
    )
    Column(verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2), horizontalAlignment = Alignment.CenterHorizontally) {
        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3)) {
                row.forEach { KeyButton(it.toString(), enabled = !busy) { press(it) } }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3)) {
            Spacer(Modifier.size(KEY_SIZE))
            KeyButton("0", enabled = !busy) { press('0') }
            IconButton(
                onClick = { digits = digits.dropLast(1) },
                enabled = !busy && digits.isNotEmpty(),
                modifier = Modifier.size(KEY_SIZE),
            ) {
                Icon(Icons.Filled.Clear, contentDescription = Strings.pinKeypadDelete)
            }
        }
    }

    if (offerForgot) SheetTextButton(Strings.pinForgot, onForgotPin)
}

private val KEY_SIZE = VibeyFinTokens.Size.buttonLarge + VibeyFinTokens.Space.s2

@Composable
private fun KeyButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(KEY_SIZE), contentPadding = PaddingValues(0.dp)) {
        Text(label, style = MaterialTheme.typography.headlineSmall)
    }
}

/** Four dots, [filled] of them lit. Read out as one description. */
@Composable
fun PinDotsRow(filled: Int, description: String = Strings.pinDigitsEntered(filled), modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3),
    ) {
        repeat(PIN_LENGTH) { index ->
            Box(
                Modifier.size(VibeyFinTokens.Space.s4).clip(CircleShape).background(
                    if (index < filled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                ),
            )
        }
    }
}

@Composable
private fun ForgotContent(prompt: PinPrompt, onSubmitPassword: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    val submit = { if (!prompt.checking) onSubmitPassword(password) }

    PasswordField(password, { password = it }, onGo = submit, error = prompt.error)
    BusyButton(Strings.pinForgotSubmit, submit, busy = prompt.checking)
}
