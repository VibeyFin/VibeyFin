package app.vibeyfin.app.components

import android.view.WindowManager
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.ui.common.Strings

/**
 * The bottom sheet every picker and menu uses. It opens straight to its full height instead of rising in two stages,
 * so rows are already in place when it appears.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VibeyFinSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // A fixed top margin: M3's own top inset shrinks as the sheet moves, which resizes a nearly full sheet and snaps it back mid-drag
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
        contentWindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom) },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        // No stretch at the ends of a list. Scrollable so a drag on a title or a row reaches the absorber as a list's does.
        CompositionLocalProvider(LocalOverscrollFactory provides null) {
            Column(
                Modifier.fillMaxWidth().nestedScroll(remember { LeftoverFlingAbsorber() }).scrollable(rememberScrollableState { 0f }, Orientation.Vertical),
                content = content,
            )
        }
    }
}

// M3 settles a sheet that never moved with the fling's speed, which throws it and springs it back. Only pulling down moves a sheet at rest.
internal class LeftoverFlingAbsorber : NestedScrollConnection {
    private var sheetMoved = false

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput && available.y > 0f) sheetMoved = true
        return if (available.y < 0f) available else Offset.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        val moved = sheetMoved

        sheetMoved = false
        return if (moved) Velocity.Zero else available
    }
}

// A sheet is its own window and shows the system bars the player hides, so they are hidden again for it
@Composable
fun KeepSystemBarsHidden() {
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
fun SheetColumn(
    onCancel: (() -> Unit)?,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    footnote: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = VibeyFinTokens.Space.s5, vertical = VibeyFinTokens.Space.s2),
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s4),
    ) {
        content()
        onCancel?.let { SheetTextButton(Strings.cancel, it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        footnote?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(VibeyFinTokens.Space.s2))
    }
}

@Composable
fun SheetTextButton(label: String, onClick: () -> Unit, color: Color = MaterialTheme.colorScheme.onSurface) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(VibeyFinTokens.Size.touch)) {
        Text(label, color = color)
    }
}

@Composable
fun SheetTitle(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
}
