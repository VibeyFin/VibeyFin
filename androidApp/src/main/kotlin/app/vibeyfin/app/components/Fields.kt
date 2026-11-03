package app.vibeyfin.app.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.vibeyfin.R
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.ui.common.Strings

/**
 * Text fields focus in the text color. Material's default is the primary color, and aqua is
 * reserved for the single primary action on a screen. The resting border is `outline` (line-strong):
 * a field's edge has to be seen, which the decorative `outlineVariant` is not for.
 */
@Composable
fun vibeyFinTextFieldColors(): TextFieldColors {
    val scheme = MaterialTheme.colorScheme

    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor = scheme.onSurface,
        unfocusedBorderColor = scheme.outline,
        focusedLabelColor = scheme.onSurface,
        unfocusedLabelColor = scheme.onSurfaceVariant,
        cursorColor = scheme.onSurface,
        focusedPlaceholderColor = scheme.onSurfaceVariant,
        unfocusedPlaceholderColor = scheme.onSurfaceVariant,
    )
}

/** A server's address: no capitals or corrections, the URL keyboard, and the reason it failed (or [help]) under it. */
@Composable
fun AddressField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    onGo: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    help: String? = null,
    error: String? = null,
    enabled: Boolean = true,
) {
    FormField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier,
        keyboardType = KeyboardType.Uri,
        onGo = onGo,
        placeholder = placeholder,
        help = help,
        error = error,
        enabled = enabled,
    )
}

/** A Jellyfin username, offered by autofill. [marked] outlines it in red when the password field under it says why. */
@Composable
fun UsernameField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, marked: Boolean = false) {
    FormField(
        value = value,
        onValueChange = onValueChange,
        label = Strings.username,
        modifier = modifier,
        marked = marked,
        contentType = ContentType.Username,
    )
}

/** A password, hidden until the eye shows it, offered by autofill, with the reason it failed under it. */
@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    onGo: () -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    enabled: Boolean = true,
) {
    var shown by rememberSaveable { mutableStateOf(false) }

    FormField(
        value = value,
        onValueChange = onValueChange,
        label = Strings.password,
        modifier = modifier,
        keyboardType = KeyboardType.Password,
        onGo = onGo,
        error = error,
        enabled = enabled,
        contentType = ContentType.Password,
        visualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { shown = !shown }) {
                Icon(
                    painter = painterResource(if (shown) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                    contentDescription = if (shown) Strings.hidePassword else Strings.showPassword,
                )
            }
        },
    )
}

@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    onGo: (() -> Unit)? = null,
    placeholder: String? = null,
    help: String? = null,
    error: String? = null,
    marked: Boolean = error != null,
    enabled: Boolean = true,
    contentType: ContentType? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val focusManager = LocalFocusManager.current

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = true,
        enabled = enabled,
        isError = marked,
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        supportingText = when {
            error != null -> {
                { AnnouncedErrorWithIcon(error) }
            }

            help != null -> {
                { Text(help) }
            }

            else -> null
        },
        colors = vibeyFinTextFieldColors(),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = keyboardType,
            imeAction = if (onGo != null) ImeAction.Go else ImeAction.Next,
        ),
        // Go (or Enter on a keyboard) puts the keyboard away as it submits
        keyboardActions = if (onGo != null) {
            KeyboardActions(onGo = {
                focusManager.clearFocus()
                onGo()
            })
        } else {
            KeyboardActions.Default
        },
        modifier = modifier.fillMaxWidth().semantics {
            contentType?.let { this.contentType = it }
            if (error != null) error(error)
        },
    )
}

@Composable
private fun AnnouncedErrorWithIcon(message: String) {
    Row(
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s1),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(painterResource(R.drawable.ic_seerr_failed), contentDescription = null, modifier = Modifier.size(16.dp))
        Text(message)
    }
}
