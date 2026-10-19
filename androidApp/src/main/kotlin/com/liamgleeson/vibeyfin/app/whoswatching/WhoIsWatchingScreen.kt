package com.liamgleeson.vibeyfin.app.whoswatching

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
import com.liamgleeson.vibeyfin.app.components.vibeyFinTextFieldColors
import com.liamgleeson.vibeyfin.app.theme.VibeyFinPalette
import com.liamgleeson.vibeyfin.domain.model.Profile
import com.liamgleeson.vibeyfin.ui.whoswatching.SignInSheet
import com.liamgleeson.vibeyfin.ui.whoswatching.WhoIsWatchingUiState
import com.liamgleeson.vibeyfin.ui.whoswatching.WhoIsWatchingViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun WhoIsWatchingRoute(
    serverId: String,
    onBack: () -> Unit,
    onSignedIn: () -> Unit,
    viewModel: WhoIsWatchingViewModel = koinViewModel(parameters = { parametersOf(serverId) }),
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
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp)) {
            TextButton(onClick = onBack) {
                Text("Back", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.serverName?.let { serverName ->
                Text(
                    text = serverName,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "Who's watching?",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(16.dp))
            if (!state.loading && !state.serverReachable) {
                UnreachableBanner(onRetry)
            }
            state.error.takeIf { state.sheet == null }?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
            }
            if (state.loading) {
                Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(120.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.profiles, key = { it.userId }) { profile ->
                        ProfileTile(profile, enabled = !state.signingIn) { onProfile(profile) }
                    }
                    if (state.quickConnectAvailable) {
                        item(key = "quick-connect") {
                            ActionTile("Quick Connect", symbol = "#", enabled = !state.signingIn, onClick = onQuickConnect)
                        }
                    }
                    item(key = "other-user") {
                        ActionTile("Other user", symbol = "+", enabled = !state.signingIn, onClick = onOtherUser)
                    }
                    item(span = { GridItemSpan(maxLineSpan) }, key = "bottom-space") { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
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

@Composable
private fun UnreachableBanner(onRetry: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Can't reach this server",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                text = "Profiles saved on this device still show, but signing in needs the server.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            TextButton(onClick = onRetry) { Text("Try again", color = MaterialTheme.colorScheme.onSurface) }
        }
    }
}

@Composable
private fun ProfileTile(profile: Profile, enabled: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box {
            Avatar(profile)
            if (profile.locked) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(26.dp)
                        .background(VibeyFinPalette.Ink.copy(alpha = 0.85f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "Needs a password",
                        tint = VibeyFinPalette.Text,
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
        modifier = Modifier.size(104.dp).clip(RoundedCornerShape(16.dp)).background(tintFor(profile.name)),
        contentAlignment = Alignment.Center,
    ) {
        // The letter goes once the picture is there; pictures with transparent areas would show it through.
        if (!pictureShown) {
            Text(
                text = profile.name.take(1).uppercase(),
                style = MaterialTheme.typography.displaySmall,
                color = VibeyFinPalette.Text,
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

/** A tile that isn't a person: "Other user", "Quick Connect". */
@Composable
private fun ActionTile(label: String, symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier.size(104.dp).clip(RoundedCornerShape(16.dp)).background(VibeyFinPalette.SurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text(symbol, style = MaterialTheme.typography.displaySmall, color = VibeyFinPalette.TextMuted)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** A muted, stable colour per name, so the same person always gets the same square. */
private fun tintFor(name: String): Color {
    val tints = listOf(0xFF3A4A6B, 0xFF4B3F6B, 0xFF3F6B5E, 0xFF6B4B3F, 0xFF6B3F55, 0xFF55633F)
    return Color(tints[(name.lowercase().hashCode() and 0x7FFFFFFF) % tints.size])
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
        containerColor = MaterialTheme.colorScheme.surface,
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
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Quick Connect",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        when {
            sheet.message != null -> {
                Text(sheet.message!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Try again") }
            }
            sheet.code != null -> {
                Text(
                    text = "On a device that's signed in to Jellyfin, open your user menu, choose Quick Connect and enter this code.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = sheet.code!!,
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                )
                WaitingLabel("Waiting for approval")
            }
            else -> WaitingLabel("Getting a code")
        }
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
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
        Spacer(Modifier.size(8.dp))
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
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = if (sheet is SignInSheet.Password) "Sign in as ${sheet.profile.name}" else "Sign in as another user",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (isOtherUser) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
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
                label = { Text("Password") },
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
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (signingIn) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(12.dp))
                    Text("Signing in")
                } else {
                    Text("Sign in")
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
