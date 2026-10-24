package app.vibeyfin.tv.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.vibeyfin.design.VibeyFinTokens
import app.vibeyfin.domain.model.Profile
import app.vibeyfin.tv.components.FocusSurface
import app.vibeyfin.tv.components.PrimaryAction
import app.vibeyfin.tv.components.SecondaryAction
import app.vibeyfin.tv.components.TvTextField
import app.vibeyfin.tv.theme.TvMarginHorizontal
import app.vibeyfin.tv.theme.TvMarginVertical
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.whoswatching.SignInSheet
import app.vibeyfin.ui.whoswatching.WhoIsWatchingViewModel
import coil3.compose.AsyncImage
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

private val Avatar = 160.dp

@Composable
fun WhoIsWatchingRoute(
    serverId: String,
    onSignedIn: () -> Unit,
    viewModel: WhoIsWatchingViewModel = koinViewModel(parameters = { parametersOf(serverId) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.signedIn) { if (state.signedIn) onSignedIn() }
    val first = remember { FocusRequester() }
    LaunchedEffect(state.loading) { if (!state.loading) runCatching { first.requestFocus() } }

    Column(
        Modifier.fillMaxSize().padding(horizontal = TvMarginHorizontal, vertical = TvMarginVertical),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            state.serverName?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(Strings.whoIsWatching, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        }
        if (!state.loading && !state.serverReachable) Text(Strings.cantReachServerTitle, color = MaterialTheme.colorScheme.error)
        state.error.takeIf { state.sheet == null }?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp)) {
            itemsIndexed(state.profiles, key = { _, it -> it.userId }) { index, profile ->
                ProfileTile(profile, if (index == 0) Modifier.focusRequester(first) else Modifier) { viewModel.onProfileTapped(profile) }
            }
            if (state.quickConnectAvailable) item(key = "quick-connect") { ActionTile(Strings.quickConnect, "#", Modifier) { viewModel.onQuickConnectTapped() } }
            item(key = "other") {
                ActionTile(Strings.otherUser, "+", if (state.profiles.isEmpty()) Modifier.focusRequester(first) else Modifier) { viewModel.onOtherUserTapped() }
            }
        }
    }

    state.sheet?.let { sheet ->
        Dialog(onDismissRequest = viewModel::onDismissSheet) {
            Column(
                Modifier.background(VibeyFinTokens.Colors.surface1, RoundedCornerShape(16.dp)).padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (sheet is SignInSheet.QuickConnect) {
                    Text(Strings.quickConnect, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                    when {
                        sheet.message != null -> {
                            Text(sheet.message!!, color = MaterialTheme.colorScheme.error)
                            PrimaryAction(Strings.tryAgain, viewModel::onQuickConnectTapped)
                        }
                        sheet.code != null -> {
                            Text(Strings.quickConnectHelp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(sheet.code!!, style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text(Strings.quickConnectWaiting, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        else -> Text(Strings.quickConnectGettingCode, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    SecondaryAction(Strings.cancel, viewModel::onDismissSheet)
                } else {
                    PasswordForm(sheet, state.error, state.signingIn, viewModel)
                }
            }
        }
    }
}

@Composable
private fun PasswordForm(sheet: SignInSheet, error: String?, signingIn: Boolean, viewModel: WhoIsWatchingViewModel) {
    var username by rememberSaveable(sheet) { mutableStateOf("") }
    var password by rememberSaveable(sheet) { mutableStateOf("") }
    val other = sheet is SignInSheet.OtherUser
    val submit = {
        if (!signingIn && (!other || username.isNotBlank())) {
            if (sheet is SignInSheet.Password) viewModel.onSubmitPassword(password) else viewModel.onSubmitOtherUser(username, password)
        }
    }
    Text(
        if (sheet is SignInSheet.Password) Strings.signInAs(sheet.profile.name) else Strings.signInAsAnotherUser,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
    if (other) TvTextField(username, { username = it }, Strings.username)
    TvTextField(password, { password = it }, Strings.password, password = true, imeAction = ImeAction.Go, onGo = submit)
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    PrimaryAction(if (signingIn) Strings.signingIn else Strings.signIn, submit)
    SecondaryAction(Strings.cancel, viewModel::onDismissSheet)
}

@Composable
private fun ProfileTile(profile: Profile, modifier: Modifier, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FocusSurface(onClick = onClick, modifier = modifier.size(Avatar)) {
            val tints = VibeyFinTokens.Colors.avatars
            Box(Modifier.fillMaxSize().background(tints[(profile.name.lowercase().hashCode() and 0x7FFFFFFF) % tints.size]), contentAlignment = Alignment.Center) {
                Text(profile.name.take(1).uppercase(), style = MaterialTheme.typography.displaySmall, color = VibeyFinTokens.Colors.ink)
                profile.avatarUrl?.let { AsyncImage(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            }
        }
        Text(profile.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        if (profile.locked) Text(Strings.needsPassword, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ActionTile(label: String, symbol: String, modifier: Modifier, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FocusSurface(onClick = onClick, modifier = modifier.size(Avatar)) {
            Box(Modifier.fillMaxSize().background(VibeyFinTokens.Colors.surface3), contentAlignment = Alignment.Center) {
                Text(symbol, style = MaterialTheme.typography.displaySmall, color = VibeyFinTokens.Colors.inkMuted)
            }
        }
        Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
