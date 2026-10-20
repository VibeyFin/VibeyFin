package com.liamgleeson.vibeyfin.app.item

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liamgleeson.vibeyfin.R
import com.liamgleeson.vibeyfin.app.theme.screenMargin
import com.liamgleeson.vibeyfin.app.components.Artwork
import com.liamgleeson.vibeyfin.app.components.BackButtonSpace
import com.liamgleeson.vibeyfin.app.components.ActionSnackbar
import com.liamgleeson.vibeyfin.app.components.FloatingBackButton
import com.liamgleeson.vibeyfin.app.components.LoadError
import com.liamgleeson.vibeyfin.app.theme.VibeyFinTokens
import com.liamgleeson.vibeyfin.domain.model.MediaItem
import com.liamgleeson.vibeyfin.ui.home.HomeExit
import com.liamgleeson.vibeyfin.ui.item.ItemUiState
import com.liamgleeson.vibeyfin.ui.common.Strings
import com.liamgleeson.vibeyfin.ui.item.ItemViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ItemRoute(
    itemId: String,
    onBack: () -> Unit,
    onPlay: (startOver: Boolean) -> Unit,
    onExit: (HomeExit) -> Unit,
    viewModel: ItemViewModel = koinViewModel(parameters = { parametersOf(itemId) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.exit) {
        state.exit?.let(onExit)
    }
    ItemScreen(
        state = state,
        onBack = onBack,
        onPlay = onPlay,
        onRetry = viewModel::onRetry,
        onTogglePlayed = viewModel::onTogglePlayed,
        onRetryAction = viewModel::onRetryAction,
        onDismissMessage = viewModel::onDismissMessage,
    )
}

@Composable
fun ItemScreen(
    state: ItemUiState,
    onBack: () -> Unit,
    onPlay: (startOver: Boolean) -> Unit,
    onRetry: () -> Unit,
    onTogglePlayed: () -> Unit,
    onRetryAction: () -> Unit,
    onDismissMessage: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                val item = state.item
                when {
                    state.loading -> {
                        Spacer(Modifier.height(BackButtonSpace))
                        Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    state.message != null -> {
                        Spacer(Modifier.height(BackButtonSpace))
                        LoadError(message = state.message!!, onRetry = onRetry, modifier = Modifier.padding(horizontal = screenMargin()))
                    }
                    item != null -> ItemDetails(item, state, onPlay)
                }
            }
            FloatingBackButton(onBack)
            if (state.canPlay) OverflowMenu(state.markLabel, onTogglePlayed)
            ActionSnackbar(state.actionMessage, onRetry = onRetryAction, onDismiss = onDismissMessage)
        }
    }
}

@Composable
private fun ItemDetails(item: MediaItem, state: ItemUiState, onPlay: (startOver: Boolean) -> Unit) {
    val facts = state.facts
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = screenMargin()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(BackButtonSpace - 12.dp))
        // A wide backdrop when the server has one; otherwise the item's own picture at its own shape.
        val picture = item.backdropUrl ?: item.posterUrl
        if (picture != null) {
            val ratio = if (item.backdropUrl != null) 16f / 9f else item.posterAspectRatio
            Artwork(
                url = picture,
                title = item.title,
                aspectRatio = ratio,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(VibeyFinTokens.Radius.artwork)),
            )
        }
        item.seriesName?.let {
            Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (facts.isNotEmpty()) {
            Text(
                text = facts.joinToString("  ·  "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.canPlay) PlayButtons(state.playLabel, state.canStartOver, onPlay)
        if (item.genres.isNotEmpty()) {
            Text(
                text = item.genres.joinToString("  ·  "),
                style = MaterialTheme.typography.bodyMedium,
                color = VibeyFinTokens.Colors.ink,
            )
        }
        item.overview?.let {
            Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
        }
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * The one filled action on the page: aqua, 56 dp tall, reading "Play" or "Resume from 41:12". "Start over"
 * sits beside it as the plain secondary button, only when there is something to resume.
 */
@Composable
private fun PlayButtons(label: String, canStartOver: Boolean, onPlay: (startOver: Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3), modifier = Modifier.fillMaxWidth()) {
        Button(onClick = { onPlay(false) }, modifier = Modifier.weight(3f).height(56.dp)) {
            Icon(painter = painterResource(R.drawable.ic_play), contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
        if (canStartOver) {
            FilledTonalButton(onClick = { onPlay(true) }, modifier = Modifier.weight(2f).height(56.dp)) {
                Text(Strings.startOver, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}

/** The "⋮" chip in the top-right corner, opposite the back button, and the menu it opens: Mark as watched or unwatched. */
@Composable
private fun BoxScope.OverflowMenu(markLabel: String, onTogglePlayed: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(end = screenMargin() - 4.dp, top = 8.dp)) {
        Box(
            modifier = Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button) { open = true },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(VibeyFinTokens.Colors.scrim),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = Strings.moreActions,
                    tint = VibeyFinTokens.Colors.ink,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(markLabel) }, onClick = { open = false; onTogglePlayed() })
        }
    }
}
