package app.vibeyfin.app.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.vibeyfin.app.navigation.LocalBottomBarPadding
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.ui.common.Strings

/**
 * Says that a change couldn't be saved, with "Try again", at the bottom of the screen: never a dialog
 * for something that doesn't block anything. [message] null shows nothing. Put it last in the [Box] that
 * fills the screen. A message that only informs passes a null [actionLabel] and a shorter [duration]; [onRetry]
 * then never runs.
 */
@Composable
fun BoxScope.ActionSnackbar(
    message: String?,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    actionLabel: String? = Strings.tryAgain,
    duration: SnackbarDuration = SnackbarDuration.Long,
) {
    val host = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect

        when (host.showSnackbar(text, actionLabel = actionLabel, duration = duration)) {
            SnackbarResult.ActionPerformed -> onRetry()
            SnackbarResult.Dismissed -> onDismiss()
        }
    }

    SnackbarHost(
        hostState = host,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .safeDrawingPadding()
            .padding(bottom = LocalBottomBarPadding.current)
            .padding(VibeyFinTokens.Space.s2),
    ) { data ->
        Snackbar(data, actionColor = MaterialTheme.colorScheme.inverseOnSurface)
    }
}
