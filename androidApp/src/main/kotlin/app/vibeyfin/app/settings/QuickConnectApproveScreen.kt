package app.vibeyfin.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vibeyfin.app.components.BackButtonSpace
import app.vibeyfin.app.components.BusyButton
import app.vibeyfin.app.components.CodeField
import app.vibeyfin.app.components.FloatingBackButton
import app.vibeyfin.app.navigation.verticalPageScroll
import app.vibeyfin.app.theme.ContentMaxWidth
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.app.theme.pageInsetsPadding
import app.vibeyfin.app.theme.screenMargin
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.quickconnect.ApproveQuickConnectUiState
import app.vibeyfin.ui.quickconnect.ApproveQuickConnectViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun QuickConnectApproveRoute(onBack: () -> Unit, viewModel: ApproveQuickConnectViewModel = koinViewModel()) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().pageInsetsPadding().verticalPageScroll(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(BackButtonSpace))
                QuickConnectApproveContent(viewModel, Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(horizontal = screenMargin()))
            }

            FloatingBackButton(onBack)
        }
    }
}

@Composable
internal fun QuickConnectApproveContent(viewModel: ApproveQuickConnectViewModel, modifier: Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    QuickConnectApproveBody(state, viewModel::onCodeChange, viewModel::onApprove, modifier)
}

@Composable
private fun QuickConnectApproveBody(state: ApproveQuickConnectUiState, onCodeChange: (String) -> Unit, onApprove: () -> Unit, modifier: Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s4)) {
        Text(Strings.quickConnect, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
        Text(
            text = Strings.quickConnectApproveLead(state.profileName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CodeField(
            value = state.code,
            onValueChange = onCodeChange,
            label = Strings.quickConnectCodeLabel,
            onGo = onApprove,
            placeholder = Strings.quickConnectCodePlaceholder,
            error = state.error,
            enabled = !state.approving,
        )
        state.done?.let { Done(it) }
        BusyButton(
            label = Strings.quickConnectApprove,
            onClick = onApprove,
            busy = state.approving,
            busyLabel = Strings.quickConnectApproving,
            enabled = state.canApprove,
        )
    }
}

@Composable
private fun Done(text: String) {
    Row(
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
