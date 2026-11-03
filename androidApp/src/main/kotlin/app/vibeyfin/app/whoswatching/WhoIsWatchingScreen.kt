package app.vibeyfin.app.whoswatching

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.lerp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vibeyfin.R
import app.vibeyfin.app.components.HeaderButton
import app.vibeyfin.app.components.TabHeader
import app.vibeyfin.app.components.VibeyFinMenu
import app.vibeyfin.app.components.VibeyFinMenuItem
import app.vibeyfin.app.components.skeletonProfiles
import app.vibeyfin.app.settings.PinSheet
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.app.theme.animationsEnabled
import app.vibeyfin.app.theme.screenMargin
import app.vibeyfin.domain.model.Profile
import app.vibeyfin.domain.model.ProfileRef
import app.vibeyfin.ui.common.AvatarTint
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.whoswatching.WhoIsWatchingUiState
import app.vibeyfin.ui.whoswatching.WhoIsWatchingViewModel
import coil3.compose.AsyncImage
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.roundToInt

private const val DIMMED_ALPHA = 0.38f
private const val WIDE_FROM_DP = 600
private val SmallAvatar = 64.dp

@Composable
fun WhoIsWatchingRoute(
    serverId: String,
    onSwitchServer: () -> Unit,
    onServerDetails: () -> Unit,
    onSignedIn: () -> Unit,
    pinFor: ProfileRef? = null,
    onSigningIn: () -> Unit = {},
    flight: SignInFlight,
    viewModel: WhoIsWatchingViewModel = koinViewModel(parameters = { parametersOf(serverId, pinFor) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val geometry = remember { ZoomGeometry() }
    val tile = if (LocalConfiguration.current.screenWidthDp >= WIDE_FROM_DP) 140.dp else 104.dp
    val animate = animationsEnabled()

    LaunchedEffect(state.signedIn) {
        if (!state.signedIn) return@LaunchedEffect
        onSigningIn()

        val profile = state.profiles.find { it.userId == state.openedUserId }
        val from = profile?.let { geometry.tiles[it.userId] }

        if (animate && profile != null && from != null) {
            flight.start(profile, from, tile, geometry.screen, geometry.button, onSignedIn)
        } else {
            onSignedIn()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onAppResumed() }

    WhoIsWatchingScreen(
        state = state,
        onSwitchServer = onSwitchServer,
        onServerDetails = onServerDetails,
        onProfile = viewModel::onProfileTapped,
        onOtherUser = viewModel::onOtherUserTapped,
        onQuickConnect = viewModel::onQuickConnectTapped,
        onRetry = viewModel::refresh,
        tile = tile,
        geometry = geometry,
        zoom = { flight.grow.value },
    )

    state.pinPrompt?.let { prompt ->
        PinSheet(prompt, viewModel::onSubmitPin, viewModel::onForgotPin, viewModel::onSubmitPassword, viewModel::onDismissPin)
    }

    state.sheet?.let { sheet ->
        SignInSheet(
            sheet = sheet,
            state = state,
            onSubmitPassword = viewModel::onSubmitPassword,
            onSubmitOtherUser = viewModel::onSubmitOtherUser,
            onQuickConnect = viewModel::onQuickConnectTapped,
            onUsePassword = viewModel::onOtherUserTapped,
            onDismiss = viewModel::onDismissSheet,
        )
    }
}

@Composable
private fun WhoIsWatchingScreen(
    state: WhoIsWatchingUiState,
    onSwitchServer: () -> Unit,
    onServerDetails: () -> Unit,
    onProfile: (Profile) -> Unit,
    onOtherUser: () -> Unit,
    onQuickConnect: () -> Unit,
    onRetry: () -> Unit,
    tile: Dp,
    geometry: ZoomGeometry,
    zoom: () -> Float = { 0f },
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().onGloballyPositioned { geometry.screen = it.rootBounds() }) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 1f - zoom().coerceAtMost(1f) }
                    .safeDrawingPadding()
                    .padding(horizontal = screenMargin()),
            ) {
                TabHeader(
                    title = Strings.whoIsWatching,
                    horizontalPadding = 0.dp,
                    trailing = {
                        ServerButton(
                            state.serverName.orEmpty(),
                            onSwitchServer,
                            onServerDetails,
                            Modifier.onGloballyPositioned { geometry.button = it.rootBounds() },
                        )
                    },
                )
                state.serverName?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(VibeyFinTokens.Space.s4))

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(tile + VibeyFinTokens.Space.s4),
                    verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s5),
                    horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s4),
                    userScrollEnabled = !state.loading,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (state.loading) {
                        skeletonProfiles(count = 4, avatarSize = tile)
                    } else {
                        notes(state, onRetry)
                        tiles(state, tile, geometry, onProfile, onQuickConnect, onOtherUser)
                    }
                }
            }
        }
    }
}

private class ZoomGeometry {
    var screen = Rect.Zero
    var button = Rect.Zero
    val tiles = mutableMapOf<String, Rect>()
}

private fun LayoutCoordinates.rootBounds() = Rect(positionInRoot(), size.toSize())

private fun LazyGridScope.fullWidth(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

private fun LazyGridScope.notes(state: WhoIsWatchingUiState, onRetry: () -> Unit) {
    state.notice?.let { notice -> fullWidth("notice") { Note(notice) } }

    if (!state.serverReachable) fullWidth("unreachable") { UnreachableCard(state, onRetry) }

    state.error.takeIf { state.sheet == null }?.let { error ->
        fullWidth("error") { Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
    }
}

private fun LazyGridScope.tiles(
    state: WhoIsWatchingUiState,
    size: Dp,
    geometry: ZoomGeometry,
    onProfile: (Profile) -> Unit,
    onQuickConnect: () -> Unit,
    onOtherUser: () -> Unit,
) {
    items(state.profiles, key = { it.userId }) { profile ->
        val opening = state.openingUserId == profile.userId
        Tile(
            label = profile.name,
            description = Strings.profileTile(profile.name, profile.locked, profile.pinRequiredToOpen, opening),
            dimmed = state.busy && !opening,
            enabled = !state.busy,
            onClick = { onProfile(profile) },
        ) {
            Avatar(profile, size, Modifier.onGloballyPositioned { geometry.tiles[profile.userId] = it.rootBounds() })
            if (profile.locked || profile.pinRequiredToOpen) LockBadge()
            if (opening) BusyOverlay()
        }
    }

    if (state.quickConnectAvailable) {
        item(key = "quick-connect") {
            ActionTile(Strings.quickConnect, Strings.quickConnectTile, ImageVector.vectorResource(R.drawable.ic_devices), size, state.busy, onQuickConnect)
        }
    }

    item(key = "other-user") {
        ActionTile(Strings.otherUser, Strings.otherUser, ImageVector.vectorResource(R.drawable.ic_person_add), size, state.busy, onOtherUser)
    }

    fullWidth("bottom-space") { Spacer(Modifier.height(VibeyFinTokens.Space.s5)) }
}

@Composable
private fun ServerButton(name: String, onSwitchServer: () -> Unit, onServerDetails: () -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }

    Box(modifier) {
        HeaderButton(
            onClick = { open = true },
            modifier = Modifier.clearAndSetSemantics {
                contentDescription = Strings.serverMenu(name)
                role = Role.Button
            },
        ) {
            Icon(painterResource(R.drawable.ic_server), contentDescription = null, tint = VibeyFinTokens.Colors.ink, modifier = Modifier.size(20.dp))
        }

        VibeyFinMenu(expanded = open, onDismiss = { open = false }) {
            VibeyFinMenuItem(Strings.switchServer, ImageVector.vectorResource(R.drawable.ic_server)) {
                open = false
                onSwitchServer()
            }
            VibeyFinMenuItem(Strings.serverDetails, Icons.Filled.Info) {
                open = false
                onServerDetails()
            }
        }
    }
}

@Composable
internal fun Note(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2), verticalAlignment = Alignment.Top) {
        Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun UnreachableCard(state: WhoIsWatchingUiState, onRetry: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(VibeyFinTokens.Radius.m3Md),
    ) {
        Column(modifier = Modifier.padding(VibeyFinTokens.Space.s4), verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s1)) {
            Text(Strings.cantReachServer(state.serverName.orEmpty()), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
            Text(
                text = listOfNotNull(state.triedAddresses.takeIf { it.isNotEmpty() }?.let(Strings::triedAddresses), Strings.cantReachServerHelp).joinToString(" "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            TextButton(onClick = onRetry) { Text(Strings.tryAgain, color = MaterialTheme.colorScheme.onSurface) }
        }
    }
}

@Composable
private fun Tile(label: String, description: String, dimmed: Boolean, enabled: Boolean, onClick: () -> Unit, picture: @Composable BoxScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (dimmed) DIMMED_ALPHA else 1f)
            .clip(RoundedCornerShape(VibeyFinTokens.Radius.m3Md))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2),
    ) {
        Box(content = picture)
        Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, maxLines = 1)
    }
}

@Composable
private fun ActionTile(label: String, description: String, icon: ImageVector, size: Dp, busy: Boolean, onClick: () -> Unit) {
    Tile(label, description, dimmed = busy, enabled = !busy, onClick = onClick) {
        Box(
            modifier = Modifier.size(size).clip(RoundedCornerShape(VibeyFinTokens.Radius.m3Md)).background(VibeyFinTokens.Colors.surface3),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = VibeyFinTokens.Colors.inkMuted, modifier = Modifier.size(size * 0.4f))
        }
    }
}

@Composable
private fun BoxScope.LockBadge() {
    Box(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(6.dp)
            .size(26.dp)
            .background(VibeyFinTokens.Colors.scrim, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null, tint = VibeyFinTokens.Colors.ink, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun BoxScope.BusyOverlay() {
    Box(
        modifier = Modifier.matchParentSize().clip(RoundedCornerShape(VibeyFinTokens.Radius.m3Md)).background(VibeyFinTokens.Colors.scrim),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp, color = VibeyFinTokens.Colors.ink)
    }
}

@Composable
internal fun Avatar(profile: Profile, size: Dp, modifier: Modifier = Modifier) {
    val small = size < SmallAvatar

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(if (small) VibeyFinTokens.Radius.m3Xs else VibeyFinTokens.Radius.m3Md))
            .background(VibeyFinTokens.Colors.avatars[AvatarTint.of(profile.name)]),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = profile.name.take(1).uppercase(),
            style = if (small) MaterialTheme.typography.titleLarge else MaterialTheme.typography.displaySmall,
            color = VibeyFinTokens.Colors.ink,
        )
    }
}
