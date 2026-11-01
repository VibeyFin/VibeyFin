package app.vibeyfin.app.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vibeyfin.R
import app.vibeyfin.app.components.BackButtonSpace
import app.vibeyfin.app.components.FloatingBackButton
import app.vibeyfin.app.components.VibeyFinSheet
import app.vibeyfin.app.components.vibeyFinTextFieldColors
import app.vibeyfin.app.theme.ContentMaxWidth
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.app.theme.screenMargin
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.seerr.SeerrFix
import app.vibeyfin.ui.seerr.SeerrPasswordSheet
import app.vibeyfin.ui.seerr.SeerrSettingsLayout
import app.vibeyfin.ui.seerr.SeerrSettingsUiState
import app.vibeyfin.ui.seerr.SeerrSettingsViewModel
import org.koin.androidx.compose.koinViewModel

/** Settings › Seerr on a phone: its own page, because it has the address field the shared Settings rows don't. */
@Composable
fun SeerrSettingsRoute(onBack: () -> Unit, onMyRequests: () -> Unit = {}, viewModel: SeerrSettingsViewModel = koinViewModel()) {
    val content = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(horizontal = screenMargin())

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().safeDrawingPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(BackButtonSpace))
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SeerrSettingsContent(viewModel, content, onLeave = onBack, onMyRequests = onMyRequests)
                }
            }

            FloatingBackButton(onBack)
        }
    }
}

/** The Seerr page's content with its sheet and dialog; the phone's page and the tablet's right pane both draw it. */
@Composable
internal fun SeerrSettingsContent(viewModel: SeerrSettingsViewModel, modifier: Modifier, onLeave: () -> Unit = {}, onMyRequests: (() -> Unit)? = null) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.leave) { if (state.leave) onLeave() }

    SeerrSettingsBody(state, viewModel, modifier, onMyRequests)

    state.passwordSheet?.let { sheet -> SeerrPasswordSheetView(sheet, viewModel::onSubmitPassword, viewModel::onDismissPassword) }

    if (state.confirmDisconnect) {
        AlertDialog(
            onDismissRequest = viewModel::onDismissDisconnect,
            title = { Text(Strings.seerrDisconnectConfirmTitle) },
            text = { Text(Strings.seerrDisconnectConfirmMessage) },
            confirmButton = { TextButton(viewModel::onConfirmDisconnect) { Text(Strings.seerrDisconnectConfirm, color = VibeyFinTokens.Colors.danger) } },
            dismissButton = {
                TextButton(viewModel::onDismissDisconnect, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) { Text(Strings.cancel) }
            },
        )
    }
}

@Composable
private fun SeerrSettingsBody(state: SeerrSettingsUiState, viewModel: SeerrSettingsViewModel, modifier: Modifier, onMyRequests: (() -> Unit)?) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2)) {
        Text(
            text = Strings.seerr,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = VibeyFinTokens.Space.s3),
        )

        when (state.layout) {
            SeerrSettingsLayout.Setup -> AddressForm(state, viewModel::onAddressChange, viewModel::onConnect)

            SeerrSettingsLayout.SigningIn -> {
                ServerCard(state)
                SectionHeader(Strings.seerrThisProfile)
                SectionCard {
                    RowFrame {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        RowText(Strings.seerrSigningIn, null, Modifier.weight(1f))
                    }
                }
            }

            SeerrSettingsLayout.NotSignedIn -> {
                ServerCard(state)
                SectionHeader(Strings.seerrThisProfile)
                SectionCard {
                    RowFrame { RowText(Strings.seerrNotSignedIn, state.problem, Modifier.weight(1f)) }
                    when (state.fix) {
                        SeerrFix.SignInWithPassword -> OpenRow(Strings.seerrSignInWithPassword, viewModel::onSignInWithPassword)
                        SeerrFix.TryAgain -> OpenRow(Strings.tryAgain, viewModel::onRetry)
                        SeerrFix.None -> Unit
                    }
                    OpenRow(Strings.seerrChangeAddress, viewModel::onChangeAddress)
                }
                DisconnectCard(viewModel::onDisconnect)
            }

            SeerrSettingsLayout.Connected -> {
                ServerCard(state)
                SectionHeader(Strings.seerrThisProfile)
                SectionCard {
                    RowFrame {
                        RowText(
                            Strings.seerrSignedInAs(state.userName),
                            if (state.signedInWithPassword) Strings.seerrSignedInWithPassword else Strings.seerrSignedInAutomatically,
                            Modifier.weight(1f),
                        )
                    }
                    state.quotaLeft?.let { left ->
                        RowFrame {
                            RowText(Strings.seerrRequestsLeft, left, Modifier.weight(1f))
                        }
                    }
                }
                onMyRequests?.let {
                    Spacer(Modifier.height(VibeyFinTokens.Space.s3))
                    SectionCard { OpenRow(Strings.seerrMyRequests, it) }
                }
                DisconnectCard(viewModel::onDisconnect)
            }

            SeerrSettingsLayout.Hidden -> Unit
        }

        Spacer(Modifier.height(VibeyFinTokens.Space.s5))
    }
}

@Composable
private fun AddressForm(state: SeerrSettingsUiState, onChange: (String) -> Unit, onConnect: () -> Unit) {
    Text(Strings.seerrLead, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(VibeyFinTokens.Space.s2))

    val error = state.addressError

    OutlinedTextField(
        value = state.address,
        onValueChange = onChange,
        label = { Text(Strings.seerrAddress) },
        placeholder = { Text(Strings.seerrAddressPlaceholder) },
        singleLine = true,
        enabled = !state.connecting,
        isError = error != null,
        // An alert beside the reason, so it isn't told by colour alone
        trailingIcon = error?.let { { Icon(painterResource(R.drawable.ic_seerr_failed), contentDescription = null) } },
        // The reason sits under the field it is about, and is read with it
        supportingText = { Text(error ?: if (state.addressShared) Strings.seerrAddressSharedHelp else Strings.seerrAddressHelp) },
        colors = vibeyFinTextFieldColors(),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Go,
        ),
        keyboardActions = KeyboardActions(onGo = { onConnect() }),
        modifier = Modifier.fillMaxWidth().semantics { if (error != null) error(error) },
    )

    Spacer(Modifier.height(VibeyFinTokens.Space.s3))

    // The page's one primary action. It keeps its colors while busy; the ViewModel ignores a second tap.
    Button(onClick = onConnect, modifier = Modifier.fillMaxWidth().height(VibeyFinTokens.Size.buttonLarge)) {
        if (state.connecting) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            Spacer(Modifier.size(VibeyFinTokens.Space.s3))
            Text(Strings.seerrConnecting)
        } else {
            Text(Strings.connect)
        }
    }
}

@Composable
private fun ServerCard(state: SeerrSettingsUiState) {
    SectionHeader(Strings.seerrServerSection)
    SectionCard {
        ValueRow(Strings.seerrAddressRow, state.savedAddress)
        state.version?.let { ValueRow(Strings.seerrVersion, it) }
    }
    Spacer(Modifier.height(VibeyFinTokens.Space.s3))
}

@Composable
private fun ValueRow(title: String, value: String) {
    RowFrame {
        RowText(title, null)
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.weight(1f, fill = false).padding(start = VibeyFinTokens.Space.s4),
        )
    }
}

@Composable
private fun OpenRow(title: String, onClick: () -> Unit) {
    RowFrame(Modifier.clickable(role = Role.Button, onClick = onClick)) {
        RowText(title, null, Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DisconnectCard(onDisconnect: () -> Unit) {
    Spacer(Modifier.height(VibeyFinTokens.Space.s3))
    SectionCard {
        RowFrame(Modifier.clickable(role = Role.Button, onClick = onDisconnect)) {
            RowText(Strings.seerrDisconnect, null, Modifier.weight(1f), titleColor = VibeyFinTokens.Colors.danger)
        }
    }
    SectionFooter(Strings.seerrDisconnectHelp)
}

@Composable
private fun SeerrPasswordSheetView(sheet: SeerrPasswordSheet, onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    VibeyFinSheet(onDismiss) {
        // What was typed stays when the password is wrong, so a typo is a small fix
        var password by rememberSaveable { mutableStateOf("") }
        val submit = { if (!sheet.submitting) onSubmit(password) }

        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = VibeyFinTokens.Space.s5, vertical = VibeyFinTokens.Space.s2),
            verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s4),
        ) {
            Text(Strings.seerrPasswordTitle, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(
                "${Strings.seerrPasswordReason} ${Strings.seerrPasswordNotKept}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = sheet.userName,
                onValueChange = {},
                readOnly = true,
                label = { Text(Strings.seerrUser) },
                singleLine = true,
                colors = vibeyFinTextFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(Strings.password) },
                singleLine = true,
                isError = sheet.error != null,
                supportingText = sheet.error?.let { { Text(it) } },
                visualTransformation = PasswordVisualTransformation(),
                colors = vibeyFinTextFieldColors(),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { submit() }),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { submit() }, modifier = Modifier.fillMaxWidth().height(VibeyFinTokens.Size.buttonLarge)) {
                if (sheet.submitting) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.size(VibeyFinTokens.Space.s3))
                    Text(Strings.signingIn)
                } else {
                    Text(Strings.signIn)
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(Strings.cancel, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(VibeyFinTokens.Space.s2))
        }
    }
}
