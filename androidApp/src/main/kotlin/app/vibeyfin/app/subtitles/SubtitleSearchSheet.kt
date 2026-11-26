package app.vibeyfin.app.subtitles

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vibeyfin.R
import app.vibeyfin.app.components.ActionSnackbar
import app.vibeyfin.app.components.KeepSystemBarsHidden
import app.vibeyfin.app.components.OptionRow
import app.vibeyfin.app.components.PickerSearch
import app.vibeyfin.app.components.PickerSheet
import app.vibeyfin.app.components.Skeleton
import app.vibeyfin.app.components.Text
import app.vibeyfin.app.components.VibeyFinSheet
import app.vibeyfin.app.components.skeleton
import app.vibeyfin.app.components.unlessSkeleton
import app.vibeyfin.app.theme.MenuPressIndication
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.ui.common.Placeholders
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.subtitles.SubtitleAdded
import app.vibeyfin.ui.subtitles.SubtitleResultRow
import app.vibeyfin.ui.subtitles.SubtitleSearchProblem
import app.vibeyfin.ui.subtitles.SubtitleSearchUiState
import app.vibeyfin.ui.subtitles.SubtitleSearchViewModel
import org.koin.androidx.compose.koinViewModel

/**
 * "Search for subtitles" for a screen. It stays in the screen's composition while the sheet is closed, so a download that is
 * still going reaches [onAdded] after the sheet has gone.
 */
@Composable
fun SubtitleSearch(
    itemId: String,
    visible: Boolean,
    onDismiss: () -> Unit,
    onAdded: (SubtitleAdded) -> Unit,
    /** A download failed with the sheet closed: the screen says so itself. */
    onFailedWhileClosed: (String) -> Unit,
    keepSystemBarsHidden: Boolean = false,
    viewModel: SubtitleSearchViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(visible, itemId) {
        if (visible) viewModel.onOpen(itemId)
    }
    LaunchedEffect(state.added) {
        state.added?.let {
            viewModel.onAddedHandled()
            onAdded(it)
            onDismiss()
        }
    }

    LaunchedEffect(state.message, visible) {
        val message = state.message

        if (!visible && message != null) {
            viewModel.onDismissMessage()
            onFailedWhileClosed(message.text)
        }
    }

    if (visible) SubtitleSearchSheet(state, viewModel, keepSystemBarsHidden, onDismiss)
}

@Composable
private fun SubtitleSearchSheet(state: SubtitleSearchUiState, viewModel: SubtitleSearchViewModel, keepSystemBarsHidden: Boolean, onDismiss: () -> Unit) {
    var choosingLanguage by remember { mutableStateOf(false) }

    VibeyFinSheet(onDismiss) {
        if (keepSystemBarsHidden) KeepSystemBarsHidden()
        CompositionLocalProvider(LocalIndication provides MenuPressIndication) {
            Text(
                Strings.searchForSubtitles,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = VibeyFinTokens.Space.s4, vertical = VibeyFinTokens.Space.s2),
            )
            LanguageRow(state.languageName) { choosingLanguage = true }
            HorizontalDivider()
            Box(Modifier.fillMaxWidth().weight(1f, fill = false).navigationBarsPadding()) {
                val problem = state.problem

                when {
                    state.loading -> Skeleton {
                        Column {
                            repeat(SKELETON_ROWS) { ResultRow(Placeholders.subtitleResult, downloading = false, enabled = true, onClick = {}) }
                        }
                    }

                    problem != null -> Problem(problem, viewModel::onRetry)

                    else -> LazyColumn {
                        items(state.results, key = { it.id }) { result ->
                            ResultRow(result, downloading = state.downloadingId == result.id, enabled = state.downloadingId == null && !result.added) { viewModel.onChoose(result.id) }
                        }
                    }
                }
                state.message?.let { message ->
                    ActionSnackbar(message.text, onRetry = viewModel::onRetry, onDismiss = viewModel::onDismissMessage, actionLabel = Strings.tryAgain.takeIf { message.canRetry })
                }
            }
        }
    }

    if (choosingLanguage) {
        val rows = remember(state.languages) { state.languages.map { OptionRow(it.code, it.name, it.selected) } }

        PickerSheet(
            title = Strings.subtitleLanguageTitle,
            rows = rows,
            loading = false,
            message = null,
            emptyText = Strings.noLanguageMatches.takeIf { rows.isEmpty() },
            onPick = {
                viewModel.onSelectLanguage(it)
                choosingLanguage = false
            },
            onDismiss = {
                viewModel.onSearchLanguages("")
                choosingLanguage = false
            },
            onRetry = {},
            search = PickerSearch(state.languageQuery, Strings.searchLanguages, viewModel::onSearchLanguages),
            keepSystemBarsHidden = keepSystemBarsHidden,
        )
    }
}

@Composable
private fun LanguageRow(language: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().heightIn(min = VibeyFinTokens.Size.touch).clickable(role = Role.Button, onClick = onClick).padding(horizontal = VibeyFinTokens.Space.s4, vertical = VibeyFinTokens.Space.s3),
    ) {
        Text(Strings.subtitleSearchLanguage, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text(language, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ResultRow(result: SubtitleResultRow, downloading: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = VibeyFinTokens.Size.touch)
            .unlessSkeleton { clickable(enabled = enabled, onClick = onClick) }
            .alpha(if (enabled || downloading || result.added) 1f else DISABLED_ALPHA)
            .clearAndSetSemantics {
                contentDescription = result.spoken
                role = Role.Button
            }
            .padding(horizontal = VibeyFinTokens.Space.s4, vertical = VibeyFinTokens.Space.s3),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s1)) {
            Text(result.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 2)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2)) {
                if (result.isMatch) MatchBadge()
                Text(result.details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (downloading) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurface, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else if (result.added) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        } else {
            Icon(painterResource(R.drawable.ic_download), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp).skeleton(CircleShape))
        }
    }
}

@Composable
private fun MatchBadge() {
    val line = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)

    Text(
        Strings.subtitleMatch,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.border(line, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

@Composable
private fun Problem(problem: SubtitleSearchProblem, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2),
        modifier = Modifier.fillMaxWidth().padding(horizontal = VibeyFinTokens.Space.s5, vertical = VibeyFinTokens.Space.s6),
    ) {
        Text(problem.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        Text(problem.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (problem.canRetry) FilledTonalButton(onClick = onRetry) { Text(Strings.tryAgain) }
    }
}

private const val SKELETON_ROWS = 4

private const val DISABLED_ALPHA = 0.4f
