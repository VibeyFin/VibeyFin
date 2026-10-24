package app.vibeyfin.app.player

import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import app.vibeyfin.app.components.OptionList
import app.vibeyfin.app.components.OptionRow
import app.vibeyfin.app.components.VibeyFinSheet
import app.vibeyfin.design.VibeyFinTokens
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.playback.TrackOption

/**
 * Audio and subtitles in a bottom sheet, as the design asks for the Android player. The two sections
 * sit side by side, each with its own scroll, so a long list in one never pushes the other out of
 * reach. A section lists its options with a check on the one in use; one with nothing to choose is
 * left out and the other takes the whole width. The check is `ink`, not aqua, which is kept for the
 * one primary action and playback progress.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackSheet(
    audio: List<TrackOption>,
    subtitles: List<TrackOption>,
    onSelectAudio: (Int) -> Unit,
    onSelectSubtitle: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    VibeyFinSheet(onDismiss) {
        KeepSystemBarsHidden()
        Row(
            modifier = Modifier.navigationBarsPadding().padding(horizontal = VibeyFinTokens.Space.s2, vertical = VibeyFinTokens.Space.s2),
            horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2),
        ) {
            if (audio.isNotEmpty()) {
                Section(Strings.audio, audio, onSelectAudio, Modifier.weight(1f))
            }

            if (subtitles.isNotEmpty()) {
                Section(Strings.subtitles, subtitles, onSelectSubtitle, Modifier.weight(1f))
            }
        }
    }
}

/** The playback speeds in a bottom sheet, one list with a check on the one in use. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedSheet(
    speeds: List<TrackOption>,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    VibeyFinSheet(onDismiss) {
        KeepSystemBarsHidden()
        Section(
            Strings.speed,
            speeds,
            onSelect,
            Modifier.navigationBarsPadding().padding(horizontal = VibeyFinTokens.Space.s2, vertical = VibeyFinTokens.Space.s2),
        )
    }
}

// A sheet is its own window and shows the system bars the player hides, so they are hidden again for it
@Composable
private fun KeepSystemBarsHidden() {
    val view = LocalView.current

    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect

        // The flag hides the status bar at once; the controller's own hide animates, which stutters the slide
        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        WindowCompat.getInsetsController(window, view).hide(WindowInsetsCompat.Type.navigationBars())
    }
}

@Composable
private fun Section(title: String, options: List<TrackOption>, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val rows = remember(options) { options.map { OptionRow(it.index.toString(), it.label, it.selected) } }

    // Half the width each, so a long server name may need a third line
    OptionList(title, rows, onSelect = { onSelect(it.toInt()) }, modifier = modifier)
}
