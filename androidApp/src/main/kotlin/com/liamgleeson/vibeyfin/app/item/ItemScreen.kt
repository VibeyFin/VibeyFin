package com.liamgleeson.vibeyfin.app.item

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liamgleeson.vibeyfin.app.components.Artwork
import com.liamgleeson.vibeyfin.app.components.BackButtonSpace
import com.liamgleeson.vibeyfin.app.components.FloatingBackButton
import com.liamgleeson.vibeyfin.app.components.LoadError
import com.liamgleeson.vibeyfin.app.theme.VibeyFinPalette
import com.liamgleeson.vibeyfin.domain.model.MediaItem
import com.liamgleeson.vibeyfin.ui.home.HomeExit
import com.liamgleeson.vibeyfin.ui.item.ItemUiState
import com.liamgleeson.vibeyfin.ui.item.ItemViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ItemRoute(
    itemId: String,
    onBack: () -> Unit,
    onExit: (HomeExit) -> Unit,
    viewModel: ItemViewModel = koinViewModel(parameters = { parametersOf(itemId) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.exit) {
        state.exit?.let(onExit)
    }
    ItemScreen(state = state, onBack = onBack, onRetry = viewModel::onRetry)
}

@Composable
fun ItemScreen(state: ItemUiState, onBack: () -> Unit, onRetry: () -> Unit) {
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
                        LoadError(message = state.message!!, onRetry = onRetry, modifier = Modifier.padding(horizontal = 24.dp))
                    }
                    item != null -> ItemDetails(item, state.facts)
                }
            }
            FloatingBackButton(onBack)
        }
    }
}

@Composable
private fun ItemDetails(item: MediaItem, facts: List<String>) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
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
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)),
            )
        }
        item.seriesName?.let {
            Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = item.title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (facts.isNotEmpty()) {
            Text(
                text = facts.joinToString("  ·  "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (item.genres.isNotEmpty()) {
            Text(
                text = item.genres.joinToString("  ·  "),
                style = MaterialTheme.typography.bodyMedium,
                color = VibeyFinPalette.Text,
            )
        }
        item.overview?.let {
            Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
        }
        Spacer(Modifier.height(24.dp))
    }
}
