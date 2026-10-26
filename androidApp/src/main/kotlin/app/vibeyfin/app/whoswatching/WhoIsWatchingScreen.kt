package app.vibeyfin.app.whoswatching

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import app.vibeyfin.app.theme.screenMargin
import app.vibeyfin.app.components.BackButtonSpace
import app.vibeyfin.app.components.FloatingBackButton
import app.vibeyfin.app.components.skeletonProfiles
import app.vibeyfin.app.components.vibeyFinTextFieldColors
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.domain.model.Profile
import app.vibeyfin.domain.model.ProfileRef
import app.vibeyfin.app.settings.PinSheet
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.whoswatching.SignInSheet
import app.vibeyfin.ui.whoswatching.WhoIsWatchingUiState
import app.vibeyfin.ui.whoswatching.WhoIsWatchingViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/** The side of a profile's square, and of the "Quick Connect" and "Other user" tiles beside it. */
private val AVATAR_SIZE = 104.dp

@Composable
fun WhoIsWatchingRoute(
    serverId: String,
    onBack: () -> Unit,
    onSignedIn: () -> Unit,
    pinFor: ProfileRef? = null,
    viewModel: WhoIsWatchingViewModel = koinViewModel(parameters = { parametersOf(serverId, pinFor) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.signedIn) {
        if (state.signedIn) onSignedIn()
    }
    WhoIsWatchingScreen(
        state = state,
        onBack = onBack,
        onProfile = viewModel::onProfileTapped,
        onOtherUser = viewModel::onOtherUserTapped,
        onQuickConnect = viewModel::onQuickConnectTapped,
        onRetry = viewModel::refresh,
        onSubmitPassword = viewModel::onSubmitPassword,
        onSubmitOtherUser = viewModel::onSubmitOtherUser,
        onDismissSheet = viewModel::onDismissSheet,
        onSubmitPin = viewModel::onSubmitPin,
        onForgotPin = viewModel::onForgotPin,
        onDismissPin = viewModel::onDismissPin,
    )
}

@Composable
fun WhoIsWatchingScreen(
    state: WhoIsWatchingUiState,
    onBack: () -> Unit,
    onProfile: (Profile) -> Unit,
    onOtherUser: () -> Unit,
    onQuickConnect: () -> Unit,
    onRetry: () -> Unit,
    onSubmitPassword: (String) -> Unit,
    onSubmitOtherUser: (String, String) -> Unit,
    onDismissSheet: () -> Unit,
    onSubmitPin: (String) -> Unit,
    onForgotPin: () -> Unit,
    onDismissPin: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = screenMargin())) {
                if (state.loading) {
                    Header(state, onRetry)
                    Spacer(Modifier.height(VibeyFinTokens.Space.s4))
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(AVATAR_SIZE + VibeyFinTokens.Space.s4),
                        verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s5),
                        horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s4),
                        userScrollEnabled = false,
                        modifier = Modifier.fillMaxSize(),
                    ) { skeletonProfiles(count = 4, avatarSize = AVATAR_SIZE) }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(AVATAR_SIZE + VibeyFinTokens.Space.s4),
                        verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s5),
                        horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s4),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        // The heading is part of the grid, beneath the button, and scrolls up under it.
                        item(span = { GridItemSpan(maxLineSpan) }, key = "header") { Header(state, onRetry) }
                        items(state.profiles, key = { it.userId }) { profile ->
                            ProfileTile(profile, enabled = !state.signingIn) { onProfile(profile) }
                        }
                        if (state.quickConnectAvailable) {
                            item(key = "quick-connect") {
                                ActionTile(Strings.quickConnect, symbol = "#", enabled = !state.signingIn, onClick = onQuickConnect)
                            }
                        }
                        item(key = "other-user") {
                            ActionTile(Strings.otherUser, symbol = "+", enabled = !state.signingIn, onClick = onOtherUser)
                        }
                        item(span = { GridItemSpan(maxLineSpan) }, key = "bottom-space") { Spacer(Modifier.height(VibeyFinTokens.Space.s5)) }
                    }
                }
            }
            FloatingBackButton(onBack)
        }
    }
    state.pinPrompt?.let { prompt ->
        PinSheet(
            prompt = prompt,
            onSubmitPin = onSubmitPin,
            onForgotPin = onForgotPin,
            // The Forgot PIN password field shares the sign-in's submit.
            onSubmitPassword = onSubmitPassword,
            onDismiss = onDismissPin,
        )
    }
    state.sheet?.let { sheet ->
        SignInSheet(
            sheet = sheet,
            error = state.error,
            signingIn = state.signingIn,
            onSubmitPassword = onSubmitPassword,
            onSubmitOtherUser = onSubmitOtherUser,
            onQuickConnectRetry = onQuickConnect,
            onDismiss = onDismissSheet,
        )
    }
}

/** The server name, the heading, and whatever needs saying before the profiles. */
@Composable
private fun Header(state: WhoIsWatchingUiState, onRetry: () -> Unit) {
    Column {
        Spacer(Modifier.height(BackButtonSpace))
        state.serverName?.let { serverName ->
            Text(
                text = serverName,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = Strings.whoIsWatching,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (!state.loading && !state.serverReachable) {
            Spacer(Modifier.height(VibeyFinTokens.Space.s4))
            UnreachableBanner(onRetry)
        }
        state.error.takeIf { state.sheet == null }?.let {
            Spacer(Modifier.height(VibeyFinTokens.Space.s2))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun UnreachableBanner(onRetry: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(bottom = VibeyFinTokens.Space.s4),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(VibeyFinTokens.Radius.m3Md),
    ) {
        Column(modifier = Modifier.padding(VibeyFinTokens.Space.s4), verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s1)) {
            Text(
                text = Strings.cantReachServerTitle,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                text = Strings.cantReachServerHelp,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            TextButton(onClick = onRetry) { Text(Strings.tryAgain, color = MaterialTheme.colorScheme.onSurface) }
        }
    }
}

@Composable
private fun ProfileTile(profile: Profile, enabled: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(VibeyFinTokens.Radius.m3Md)).clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2),
    ) {
        Box {
            Avatar(profile)
            if (profile.locked || profile.pinRequiredToOpen) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(26.dp)
                        .background(VibeyFinTokens.Colors.scrim, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = if (profile.locked) Strings.needsPassword else Strings.needsPin,
                        tint = VibeyFinTokens.Colors.ink,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        Text(
            text = profile.name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
        )
    }
}

/** The profile's picture, or its first letter on a square tinted from the name while there is none or it loads. */
@Composable
private fun Avatar(profile: Profile) {
    var pictureShown by remember(profile.avatarUrl) { mutableStateOf(false) }
    Box(
        modifier = Modifier.size(AVATAR_SIZE).clip(RoundedCornerShape(VibeyFinTokens.Radius.m3Md)).background(tintFor(profile.name)),
        contentAlignment = Alignment.Center,
    ) {
        // The letter goes once the picture is there; pictures with transparent areas would show it through.
        if (!pictureShown) {
            Text(
                text = profile.name.take(1).uppercase(),
                style = MaterialTheme.typography.displaySmall,
                color = VibeyFinTokens.Colors.ink,
            )
        }
        profile.avatarUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onSuccess = { pictureShown = true },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** A tile that isn't a person: Strings.otherUser, Strings.quickConnect. */
@Composable
private fun ActionTile(label: String, symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(VibeyFinTokens.Radius.m3Md)).clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2),
    ) {
        Box(
            modifier = Modifier.size(AVATAR_SIZE).clip(RoundedCornerShape(VibeyFinTokens.Radius.m3Md)).background(VibeyFinTokens.Colors.surface3),
            contentAlignment = Alignment.Center,
        ) {
            Text(symbol, style = MaterialTheme.typography.displaySmall, color = VibeyFinTokens.Colors.inkMuted)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** A muted, stable colour per name (the `avatar-1` to `avatar-6` tokens), so the same person always gets the same square. */
private fun tintFor(name: String): Color {
    val tints = VibeyFinTokens.Colors.avatars
    return tints[(name.lowercase().hashCode() and 0x7FFFFFFF) % tints.size]
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SignInSheet(
    sheet: SignInSheet,
    error: String?,
    signingIn: Boolean,
    onSubmitPassword: (String) -> Unit,
    onSubmitOtherUser: (String, String) -> Unit,
    onQuickConnectRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        if (sheet is SignInSheet.QuickConnect) {
            QuickConnectContent(sheet, onQuickConnectRetry, onDismiss)
        } else {
            PasswordContent(sheet, error, signingIn, onSubmitPassword, onSubmitOtherUser, onDismiss)
        }
    }
}

@Composable
private fun QuickConnectContent(sheet: SignInSheet.QuickConnect, onRetry: () -> Unit, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = VibeyFinTokens.Space.s5, vertical = VibeyFinTokens.Space.s2),
        verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s4),
    ) {
        Text(
            text = Strings.quickConnect,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        when {
            sheet.message != null -> {
                Text(sheet.message!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                FilledTonalButton(onClick = onRetry, modifier = Modifier.fillMaxWidth().height(VibeyFinTokens.Size.touch)) { Text(Strings.tryAgain) }
            }
            sheet.code != null -> {
                Text(
                    text = Strings.quickConnectHelp,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = sheet.code!!,
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                )
                WaitingLabel(Strings.quickConnectWaiting)
            }
            else -> WaitingLabel(Strings.quickConnectGettingCode)
        }
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text(Strings.cancel, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(VibeyFinTokens.Space.s2))
    }
}

@Composable
private fun WaitingLabel(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(VibeyFinTokens.Space.s2))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PasswordContent(
    sheet: SignInSheet,
    error: String?,
    signingIn: Boolean,
    onSubmitPassword: (String) -> Unit,
    onSubmitOtherUser: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    run {
        // The fields keep what was typed when a sign-in fails, so a typo is a small fix.
        var username by rememberSaveable(sheet) { mutableStateOf("") }
        var password by rememberSaveable(sheet) { mutableStateOf("") }
        val isOtherUser = sheet is SignInSheet.OtherUser
        val canSubmit = !signingIn && (!isOtherUser || username.isNotBlank())
        val submit = {
            if (canSubmit) {
                if (sheet is SignInSheet.Password) onSubmitPassword(password) else onSubmitOtherUser(username, password)
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = VibeyFinTokens.Space.s5, vertical = VibeyFinTokens.Space.s2),
            verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s4),
        ) {
            Text(
                text = if (sheet is SignInSheet.Password) Strings.signInAs(sheet.profile.name) else Strings.signInAsAnotherUser,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (isOtherUser) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(Strings.username) },
                    singleLine = true,
                    isError = error != null,
                    colors = vibeyFinTextFieldColors(),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(Strings.password) },
                singleLine = true,
                isError = error != null,
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
            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Button(
                onClick = { submit() },
                // Stays enabled while signing in so the busy button keeps its colours; submit() ignores it.
                enabled = canSubmit || signingIn,
                modifier = Modifier.fillMaxWidth().height(VibeyFinTokens.Size.buttonLarge),
            ) {
                if (signingIn) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
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
