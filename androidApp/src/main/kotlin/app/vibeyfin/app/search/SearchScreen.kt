package app.vibeyfin.app.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import app.vibeyfin.domain.model.asMediaItem
import app.vibeyfin.app.navigation.LocalTileLongPress
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.semantics.Role
import app.vibeyfin.app.theme.ContentMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import app.vibeyfin.ui.search.SearchFilter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vibeyfin.app.components.Artwork
import app.vibeyfin.app.components.LoadError
import app.vibeyfin.app.components.SectionTitle
import app.vibeyfin.app.components.SkeletonBlock
import app.vibeyfin.app.components.SkeletonLine
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.app.theme.screenMargin
import app.vibeyfin.domain.model.SearchKind
import app.vibeyfin.domain.model.SearchResult
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.home.HomeExit
import app.vibeyfin.ui.search.SearchUiState
import app.vibeyfin.ui.search.SearchViewModel
import coil3.compose.AsyncImage
import org.koin.androidx.compose.koinViewModel

@Composable
fun SearchRoute(
    onOpenResult: (SearchResult) -> Unit,
    onExit: (HomeExit) -> Unit,
    viewModel: SearchViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.exit) {
        state.exit?.let(onExit)
    }
    SearchScreen(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onClear = viewModel::onClear,
        onRetry = viewModel::onRetry,
        onFilter = viewModel::onFilter,
        onSubmit = viewModel::onSubmit,
        onHistoryPicked = viewModel::onHistoryPicked,
        onHistoryRemoved = viewModel::onHistoryRemoved,
        onHistoryCleared = viewModel::onHistoryCleared,
        // A match that is opened keeps what was typed in the history.
        onOpenResult = { result -> viewModel.onResultOpened(); onOpenResult(result) },
    )
}

/** The Search tab: a field at the top, and under it what matches, grouped into movies, shows, episodes, people and collections. */
@Composable
fun SearchScreen(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onRetry: () -> Unit,
    onFilter: (SearchFilter) -> Unit,
    onSubmit: () -> Unit,
    onHistoryPicked: (String) -> Unit,
    onHistoryRemoved: (String) -> Unit,
    onHistoryCleared: () -> Unit,
    onOpenResult: (SearchResult) -> Unit,
) {
    val margin = screenMargin()
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // The field takes the keyboard when the tab opens with nothing typed, so a search can start at once.
    LaunchedEffect(Unit) { if (state.query.isEmpty()) focus.requestFocus() }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // On a tablet the column stays a readable width, centred.
        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = ContentMaxWidth).fillMaxSize()) {
                TextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    placeholder = { Text(if (state.offline) Strings.offlineSearchHint else Strings.searchHint) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = onClear) { Icon(Icons.Filled.Clear, contentDescription = Strings.clearSearch) }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, keyboardType = KeyboardType.Text),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit(); keyboard?.hide() }),
                    shape = RoundedCornerShape(50),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = VibeyFinTokens.Colors.surface2,
                        unfocusedContainerColor = VibeyFinTokens.Colors.surface2,
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = margin, vertical = VibeyFinTokens.Space.s3).focusRequester(focus)
                        .semantics { contentDescription = if (state.offline) Strings.offlineSearchHint else Strings.searchHint },
                )
                // One button per kind, once something is typed: All lists every group, the others only theirs.
                if (!state.showsPrompt) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = margin),
                        horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2),
                        modifier = Modifier.fillMaxWidth().padding(bottom = VibeyFinTokens.Space.s2),
                    ) {
                        items(state.chips, key = { it.filter }) { chip ->
                            FilterChip(
                                selected = chip.selected,
                                onClick = { onFilter(chip.filter) },
                                label = { Text(chip.label, maxLines = 1) },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = VibeyFinTokens.Colors.surface2,
                                    labelColor = VibeyFinTokens.Colors.inkMuted,
                                    selectedContainerColor = VibeyFinTokens.Colors.accentSoft,
                                    selectedLabelColor = VibeyFinTokens.Colors.onAccentSoft,
                                ),
                                border = null,
                            )
                        }
                    }
                }
                when {
                    state.showsPrompt && state.history.isNotEmpty() -> History(state.history, margin, onHistoryPicked, onHistoryRemoved, onHistoryCleared)
                    state.showsPrompt -> if (state.offline) Prompt(Strings.offlineSearchPrompt, Strings.offlineBannerHelp, margin) else Prompt(Strings.searchPrompt, Strings.searchPromptHelp, margin)
                    state.message != null -> LoadError(state.message!!, onRetry, modifier = Modifier.padding(horizontal = margin))
                    state.noResults -> Prompt(Strings.noSearchResults(state.searched), if (state.offline) Strings.offlineSearchEmpty else Strings.noSearchResultsHelp, margin)
                    state.groups.isEmpty() -> ResultSkeleton(margin)
                    else -> Results(state, margin, onOpenResult)
                }
            }
        }
    }
}

@Composable
private fun Prompt(title: String, help: String, margin: androidx.compose.ui.unit.Dp) {
    // Near the top, under the field: centred it would ride up with the keyboard.
    Column(
        Modifier.fillMaxSize().padding(horizontal = margin).padding(top = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = VibeyFinTokens.Colors.inkMuted, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(VibeyFinTokens.Space.s3))
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
        Spacer(Modifier.height(VibeyFinTokens.Space.s1))
        Text(help, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/** What was searched for before, newest first: a tap runs it again, the cross takes it off, "Clear all" empties the list. */
@Composable
private fun History(history: List<String>, margin: androidx.compose.ui.unit.Dp, onPick: (String) -> Unit, onRemove: (String) -> Unit, onClearAll: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = margin, vertical = VibeyFinTokens.Space.s2),
    ) {
        item(key = "header") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(Strings.recentSearches, modifier = Modifier.weight(1f).semantics { heading() })
                androidx.compose.material3.TextButton(onClick = onClearAll) {
                    Text(Strings.clearHistory, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        items(history, key = { it }) { term ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(VibeyFinTokens.Radius.m3Md)).clickable(role = Role.Button) { onPick(term) }.padding(start = VibeyFinTokens.Space.s1),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = VibeyFinTokens.Colors.inkMuted, modifier = Modifier.size(20.dp))
                Text(term, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = VibeyFinTokens.Space.s3, vertical = VibeyFinTokens.Space.s3))
                IconButton(onClick = { onRemove(term) }) {
                    Icon(Icons.Filled.Clear, contentDescription = Strings.removeFromHistory(term), tint = VibeyFinTokens.Colors.inkMuted)
                }
            }
        }
    }
}

@Composable
private fun Results(state: SearchUiState, margin: androidx.compose.ui.unit.Dp, onOpen: (SearchResult) -> Unit) {
    // The older answer stays while the next one is on its way, dimmed.
    LazyColumn(
        modifier = Modifier.fillMaxSize().alpha(if (state.loading) 0.5f else 1f),
        contentPadding = PaddingValues(horizontal = margin, vertical = VibeyFinTokens.Space.s2),
        verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2),
    ) {
        state.visibleGroups.forEach { group ->
            item(key = "heading-${group.kind}") {
                SectionTitle(group.title, modifier = Modifier.padding(top = VibeyFinTokens.Space.s3).semantics { heading() })
            }
            items(group.results, key = { "${group.kind}-${it.id}" }) { result -> ResultRow(result) { onOpen(result) } }
        }
        item(key = "bottom") { Spacer(Modifier.height(VibeyFinTokens.Space.s5)) }
    }
}

/** One match: a picture (a poster, a still, or a round portrait), the title, and a line under it. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ResultRow(result: SearchResult, onClick: () -> Unit) {
    // A long press opens the same menu as on a poster (Details, Trailer, My List); a person has none.
    val longPress = LocalTileLongPress.current
    val asItem = remember(result) { result.asMediaItem() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(VibeyFinTokens.Radius.m3Md))
            .combinedClickable(
                role = Role.Button,
                onLongClickLabel = if (longPress != null && asItem != null) Strings.showOptions else null,
                onLongClick = if (longPress != null && asItem != null) ({ longPress(asItem) }) else null,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) { contentDescription = Strings.searchResultDescription(result.title, result.subtitle) }
            .padding(vertical = VibeyFinTokens.Space.s1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3),
    ) {
        when (result.kind) {
            SearchKind.Person -> Box(Modifier.size(56.dp).clip(CircleShape).background(VibeyFinTokens.Colors.surface2), contentAlignment = Alignment.Center) {
                Text(initials(result.title), style = MaterialTheme.typography.titleSmall, color = VibeyFinTokens.Colors.inkMuted)
                if (result.imageUrl != null) {
                    AsyncImage(model = result.imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
            SearchKind.Episode -> Box(Modifier.width(112.dp).clip(RoundedCornerShape(VibeyFinTokens.Radius.artwork))) {
                Artwork(url = result.imageUrl, title = result.title, aspectRatio = 16f / 9f, modifier = Modifier.fillMaxWidth())
            }
            else -> Box(Modifier.width(56.dp).clip(RoundedCornerShape(VibeyFinTokens.Radius.artwork))) {
                Artwork(url = result.imageUrl, title = result.title, aspectRatio = 2f / 3f, modifier = Modifier.fillMaxWidth())
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(result.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis)
            result.subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Grey rows where the first answer will be. */
@Composable
private fun ResultSkeleton(margin: androidx.compose.ui.unit.Dp) {
    Column(Modifier.padding(horizontal = margin, vertical = VibeyFinTokens.Space.s2), verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3)) {
        repeat(5) {
            Row(horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3), verticalAlignment = Alignment.CenterVertically) {
                SkeletonBlock(Modifier.width(56.dp).height(84.dp))
                Column(verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s1)) {
                    SkeletonLine(180.dp, MaterialTheme.typography.titleMedium)
                    SkeletonLine(80.dp, MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

private fun initials(name: String): String =
    name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
