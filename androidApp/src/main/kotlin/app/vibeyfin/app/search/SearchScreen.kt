package app.vibeyfin.app.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vibeyfin.app.components.Artwork
import app.vibeyfin.app.components.FilterChipButton
import app.vibeyfin.app.components.LoadError
import app.vibeyfin.app.components.SectionTitle
import app.vibeyfin.app.components.SkeletonBlock
import app.vibeyfin.app.components.SkeletonLine
import app.vibeyfin.app.components.TabHeader
import app.vibeyfin.app.navigation.LocalBottomBarPadding
import app.vibeyfin.app.navigation.LocalSeerrLongPress
import app.vibeyfin.app.navigation.LocalTileLongPress
import app.vibeyfin.app.seerr.SeerrPosterTile
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.app.theme.screenMargin
import app.vibeyfin.domain.model.SearchKind
import app.vibeyfin.domain.model.SearchResult
import app.vibeyfin.domain.model.SeerrTitle
import app.vibeyfin.domain.model.asMediaItem
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.home.HomeExit
import app.vibeyfin.ui.search.SearchFilter
import app.vibeyfin.ui.search.SearchUiState
import app.vibeyfin.ui.search.SearchViewModel
import app.vibeyfin.ui.search.SeerrSearch
import coil3.compose.AsyncImage
import org.koin.androidx.compose.koinViewModel
import androidx.compose.foundation.lazy.grid.items as gridItems

@Composable
fun SearchRoute(
    onOpenResult: (SearchResult) -> Unit,
    onExit: (HomeExit) -> Unit,
    onOpenSeerr: (SeerrTitle) -> Unit = {},
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
        onSeerrRetry = viewModel::onSeerrRetry,
        onFilter = viewModel::onFilter,
        onSubmit = viewModel::onSubmit,
        onHistoryPicked = viewModel::onHistoryPicked,
        onHistoryRemoved = viewModel::onHistoryRemoved,
        onHistoryCleared = viewModel::onHistoryCleared,
        onOpenResult = { result ->
            viewModel.onResultOpened()
            onOpenResult(result)
        },
        onOpenSeerr = { title ->
            viewModel.onResultOpened()
            onOpenSeerr(title)
        },
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
    onSeerrRetry: () -> Unit = {},
    onOpenSeerr: (SeerrTitle) -> Unit = {},
    onHistoryPicked: (String) -> Unit,
    onHistoryRemoved: (String) -> Unit,
    onHistoryCleared: () -> Unit,
    onOpenResult: (SearchResult) -> Unit,
) {
    val margin = screenMargin()
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) { if (state.query.isEmpty()) focus.requestFocus() }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // The grids use the whole width, on a tablet too: more columns, not a narrow centered column
        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.fillMaxSize()) {
                TabHeader(Strings.tabSearch)
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
                    keyboardActions = KeyboardActions(onSearch = {
                        onSubmit()
                        keyboard?.hide()
                    }),
                    shape = RoundedCornerShape(50),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = VibeyFinTokens.Colors.surface2,
                        unfocusedContainerColor = VibeyFinTokens.Colors.surface2,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = margin).padding(bottom = VibeyFinTokens.Space.s3).focusRequester(focus)
                        .semantics { contentDescription = if (state.offline) Strings.offlineSearchHint else Strings.searchHint },
                )

                if (!state.showsPrompt) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = margin),
                        horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2),
                        modifier = Modifier.fillMaxWidth().padding(bottom = VibeyFinTokens.Space.s2),
                    ) {
                        items(state.chips, key = { it.filter }) { chip ->
                            FilterChipButton(
                                chip.label,
                                onClick = { onFilter(chip.filter) },
                                active = chip.selected,
                                modifier = Modifier.semantics { selected = chip.selected },
                            )
                        }
                    }
                }

                when {
                    state.showsPrompt && state.history.isNotEmpty() -> History(state.history, margin, onHistoryPicked, onHistoryRemoved, onHistoryCleared)

                    state.showsPrompt -> if (state.offline) Prompt(Strings.offlineSearchPrompt, Strings.offlineBannerHelp, margin) else Prompt(Strings.searchPrompt, Strings.searchPromptHelp, margin)

                    state.message != null -> LoadError(state.message!!, onRetry, modifier = Modifier.padding(horizontal = margin))

                    state.noResults -> Prompt(Strings.noSearchResults(state.searched), if (state.offline) Strings.offlineSearchEmpty else Strings.noSearchResultsHelp, margin)

                    // The first answer for this text is on its way
                    state.searched.isEmpty() -> ResultSkeleton(margin)

                    else -> Results(state, margin, onOpenResult, onSeerrRetry, onOpenSeerr)
                }
            }
        }
    }
}

@Composable
private fun Prompt(title: String, help: String, margin: Dp) {
    // Near the top, under the field: centered it would ride up with the keyboard
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

@Composable
private fun History(history: List<String>, margin: Dp, onPick: (String) -> Unit, onRemove: (String) -> Unit, onClearAll: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = margin, top = VibeyFinTokens.Space.s2, end = margin, bottom = VibeyFinTokens.Space.s2 + LocalBottomBarPadding.current),
    ) {
        item(key = "header") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(Strings.recentSearches, modifier = Modifier.weight(1f).semantics { heading() })
                TextButton(onClick = onClearAll) {
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
private fun Results(state: SearchUiState, margin: Dp, onOpen: (SearchResult) -> Unit, onSeerrRetry: () -> Unit, onOpenSeerr: (SeerrTitle) -> Unit) {
    val seerrLongPress = LocalSeerrLongPress.current

    // Each group is a grid under its heading: poster tiles, round portraits for people, and rows for episodes.
    // The older answer stays while the next one is on its way, dimmed.
    LazyVerticalGrid(
        columns = GridCells.Adaptive(GRID_MIN_WIDTH),
        modifier = Modifier.fillMaxSize().alpha(if (state.loading) 0.5f else 1f),
        contentPadding = PaddingValues(start = margin, top = VibeyFinTokens.Space.s2, end = margin, bottom = VibeyFinTokens.Space.s2 + LocalBottomBarPadding.current),
        horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3),
        verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s4),
    ) {
        // The library had nothing, but Seerr's group follows: say so above it (issue #14)
        if (state.libraryNoMatch) {
            item(key = "library-no-match", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    Strings.libraryNoMatch(state.searched),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = VibeyFinTokens.Space.s3),
                )
            }
        }

        state.visibleGroups.forEach { group ->
            item(key = "heading-${group.kind}", span = { GridItemSpan(maxLineSpan) }) {
                SectionTitle(group.title, modifier = Modifier.padding(top = VibeyFinTokens.Space.s3).semantics { heading() })
            }

            gridItems(group.results, key = { "${group.kind}-${it.id}" }, span = { if (it.kind == SearchKind.Episode) GridItemSpan(maxLineSpan / maxOf(1, maxLineSpan / EPISODE_COLUMNS)) else GridItemSpan(1) }) { result ->
                ResultTile(result) { onOpen(result) }
            }
        }

        state.seerrGroup?.let { seerr -> seerrGroup(seerr, titled = state.filter != SearchFilter.NotInLibrary, onSeerrRetry, onOpenSeerr, seerrLongPress) }

        item(key = "bottom", span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(VibeyFinTokens.Space.s5)) }
    }
}

// Seerr's titles that aren't in the library, always last: the heading (left out when the filter shows nothing else), a note, then
// the tiles, three grey tiles while Seerr answers, or why it didn't
@OptIn(ExperimentalFoundationApi::class)
private fun LazyGridScope.seerrGroup(seerr: SeerrSearch, titled: Boolean, onRetry: () -> Unit, onOpen: (SeerrTitle) -> Unit, onLongPress: ((SeerrTitle) -> Unit)?) {
    item(key = "seerr-heading", span = { GridItemSpan(maxLineSpan) }) {
        Column(Modifier.padding(top = VibeyFinTokens.Space.s3), verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s1)) {
            if (titled) SectionTitle(Strings.seerrNotInLibrary, modifier = Modifier.semantics { heading() })
            Text(Strings.seerrSearchNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    when (seerr) {
        SeerrSearch.Searching -> items(SEERR_SKELETON_TILES, key = { "seerr-skeleton-$it" }) {
            Column(verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2)) {
                SkeletonBlock(Modifier.fillMaxWidth().aspectRatio(2f / 3f))
                SkeletonLine(80.dp, MaterialTheme.typography.titleSmall)
            }
        }

        is SeerrSearch.Found -> gridItems(seerr.titles, key = { "seerr-${it.type}-${it.tmdbId}" }) { title ->
            // A tap opens its Seerr page; a long press its Seerr menu (Request, Details, Trailer)
            SeerrPosterTile(
                title,
                Modifier.fillMaxWidth().clip(RoundedCornerShape(VibeyFinTokens.Radius.m3Md)).combinedClickable(
                    role = Role.Button,
                    onLongClickLabel = if (onLongPress != null) Strings.showOptions else null,
                    onLongClick = onLongPress?.let { press -> { press(title) } },
                    onClick = { onOpen(title) },
                ),
            )
        }

        is SeerrSearch.Failed -> item(key = "seerr-failed", span = { GridItemSpan(maxLineSpan) }) { LoadError(seerr.message, onRetry) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ResultTile(result: SearchResult, onClick: () -> Unit) {
    // A long press opens the same menu as on a poster (Details, Trailer, My List); a person has none
    val longPress = LocalTileLongPress.current
    val asItem = remember(result) { result.asMediaItem() }
    val modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(VibeyFinTokens.Radius.m3Md))
        .combinedClickable(
            role = Role.Button,
            onLongClickLabel = if (longPress != null && asItem != null) Strings.showOptions else null,
            onLongClick = if (longPress != null && asItem != null) ({ longPress(asItem) }) else null,
            onClick = onClick,
        )
        .semantics(mergeDescendants = true) { contentDescription = Strings.searchResultDescription(result.title, result.subtitle) }
    if (result.kind == SearchKind.Episode) {
        // The show and episode number need the room a tile doesn't have, so an episode reads as a row
        Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3)) {
            Box(Modifier.width(EPISODE_STILL_WIDTH).clip(RoundedCornerShape(VibeyFinTokens.Radius.artwork))) {
                Artwork(url = result.imageUrl, title = result.title, aspectRatio = 16f / 9f, modifier = Modifier.fillMaxWidth())
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(result.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis)
                result.subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        return
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2)) {
        when (result.kind) {
            SearchKind.Person -> Box(
                Modifier.fillMaxWidth().aspectRatio(1f).clip(CircleShape).background(VibeyFinTokens.Colors.surface2),
                contentAlignment = Alignment.Center,
            ) {
                Text(Strings.initials(result.title), style = MaterialTheme.typography.titleMedium, color = VibeyFinTokens.Colors.inkMuted)

                if (result.imageUrl != null) {
                    AsyncImage(model = result.imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }

            else -> Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(VibeyFinTokens.Radius.artwork))) {
                Artwork(url = result.imageUrl, title = result.title, aspectRatio = 2f / 3f, modifier = Modifier.fillMaxWidth())
            }
        }

        val centred = if (result.kind == SearchKind.Person) TextAlign.Center else TextAlign.Start

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(result.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = centred, modifier = Modifier.fillMaxWidth())
            result.subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = centred, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ResultSkeleton(margin: Dp) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(GRID_MIN_WIDTH),
        userScrollEnabled = false,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = margin, top = VibeyFinTokens.Space.s2, end = margin, bottom = VibeyFinTokens.Space.s2 + LocalBottomBarPadding.current),
        horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3),
        verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s4),
    ) {
        items(6) {
            Column(verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2)) {
                SkeletonBlock(Modifier.fillMaxWidth().aspectRatio(2f / 3f))
                SkeletonLine(80.dp, MaterialTheme.typography.titleSmall)
            }
        }
    }
}

private val GRID_MIN_WIDTH = 110.dp

private const val SEERR_SKELETON_TILES = 3

// About 350 dp, so a phone shows one per line and a wide tablet three
private const val EPISODE_COLUMNS = 3

private val EPISODE_STILL_WIDTH = 128.dp
