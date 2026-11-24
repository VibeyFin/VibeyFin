package app.vibeyfin.app.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.playback.PlaybackUiState
import app.vibeyfin.ui.playback.PlaybackViewModel

private const val DIM_FADE_IN_MS = 1_000
private const val DIM_FADE_OUT_MS = 150

// The first touch only wakes the player; it never reaches the controls or the video underneath
@Composable
internal fun PlayerDimOverlay(dimmed: Boolean, onWake: () -> Unit) {
    val currentOnWake: () -> Unit by rememberUpdatedState(onWake)

    AnimatedVisibility(visible = dimmed, enter = fadeIn(tween(DIM_FADE_IN_MS)), exit = fadeOut(tween(DIM_FADE_OUT_MS))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(VibeyFinTokens.Colors.scrimStrong)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown().consume()
                        currentOnWake()
                    }
                }
                .semantics {
                    contentDescription = Strings.playerScreenDimmed
                    onClick {
                        currentOnWake()
                        true
                    }
                },
        )
    }
}

@Composable
internal fun PlayerScreenRestEffect(state: PlaybackUiState, window: PlayerWindow?, chrome: PlayerChrome, viewModel: PlaybackViewModel) {
    LaunchedEffect(state.keepsScreenOn) { window?.keepScreenOn(state.keepsScreenOn) }

    LaunchedEffect(state.screenDimmed) { if (state.screenDimmed) chrome.controlsVisible = false }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onScreenShown() }
}
