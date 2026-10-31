package app.vibeyfin.app.seerr

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vibeyfin.app.children.posterMinWidth
import app.vibeyfin.app.components.Artwork
import app.vibeyfin.app.components.BackButtonSpace
import app.vibeyfin.app.components.FilterChipButton
import app.vibeyfin.app.components.FloatingBackButton
import app.vibeyfin.app.components.LoadError
import app.vibeyfin.app.components.SectionTitle
import app.vibeyfin.app.components.SkeletonBlock
import app.vibeyfin.app.components.bleed
import app.vibeyfin.app.navigation.LocalBottomBarPadding
import app.vibeyfin.app.navigation.LocalSeerrLongPress
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.app.theme.isTabletWidth
import app.vibeyfin.app.theme.screenMargin
import app.vibeyfin.domain.model.RequestState
import app.vibeyfin.domain.model.SeerrMediaType
import app.vibeyfin.domain.model.SeerrTitle
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.seerr.MyRequestsViewModel
import app.vibeyfin.ui.seerr.RequestFilter
import app.vibeyfin.ui.seerr.SeerrListKind
import app.vibeyfin.ui.seerr.SeerrListViewModel
import app.vibeyfin.ui.seerr.SeerrRow
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/** A Seerr poster that opens on a tap and offers the Seerr menu on a long press. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SeerrTile(title: SeerrTitle, onOpen: (SeerrTitle) -> Unit, modifier: Modifier = Modifier, meta: String? = null, plainInLibrary: Boolean = false) {
    val longPress = LocalSeerrLongPress.current

    SeerrPosterTile(
        title,
        modifier.clip(RoundedCornerShape(VibeyFinTokens.Radius.m3Md)).combinedClickable(
            role = Role.Button,
            onLongClickLabel = if (longPress != null) Strings.showOptions else null,
            onLongClick = longPress?.let { press -> { press(title) } },
            onClick = { onOpen(title) },
        ),
        meta = meta,
        plainInLibrary = plainInLibrary,
    )
}

/**
 * A Seerr row on Home: My requests, or a discover list. Grey posters while it loads; it isn't drawn at all when empty, and a failure
 * hides it (Seerr is optional). Titles the library has look like the library's own in the discover rows.
 */
@Composable
fun SeerrRowSection(row: SeerrRow, margin: Dp, onOpen: (SeerrTitle) -> Unit, onSeeAll: (() -> Unit)?) {
    Column(verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.weight(1f)) { SectionTitle(row.title, maxLines = 1, modifier = Modifier.semantics { heading() }) }

            if (onSeeAll != null && row.titles.isNotEmpty()) {
                TextButton(onClick = onSeeAll, modifier = Modifier.semantics { contentDescription = Strings.seeAllIn(row.title) }) {
                    Text(Strings.seeAll, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (row.loading) {
            // Laid out like the real tiles, so nothing under the row moves when they arrive
            Row(Modifier.bleed(margin).padding(horizontal = margin), horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3)) {
                repeat(PLACEHOLDER_TILES) { SeerrPosterPlaceholder(Modifier.width(VibeyFinTokens.Size.posterWidth)) }
            }
        } else {
            LazyRow(
                modifier = Modifier.bleed(margin),
                contentPadding = PaddingValues(horizontal = margin),
                horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3),
            ) {
                items(row.titles, key = { "${it.type}-${it.tmdbId}" }, contentType = { "seerr-poster" }) { title ->
                    SeerrTile(
                        title,
                        onOpen,
                        Modifier.width(VibeyFinTokens.Size.posterWidth),
                        meta = if (row.showsDates) Strings.seerrReleaseDay(title.releaseDate) ?: Strings.seerrTitleMeta(title.year, title.type) else null,
                        plainInLibrary = row.kind != null,
                    )
                }
            }
        }
    }
}

/** A discover list's See all: a poster grid titled as the row, Movies and Shows chips where it holds both, more as it scrolls. */
@Composable
fun SeerrListRoute(kind: SeerrListKind, onBack: () -> Unit, onOpen: (SeerrTitle) -> Unit, viewModel: SeerrListViewModel = koinViewModel(parameters = { parametersOf(kind) })) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val margin = screenMargin()

    PageFrame(state.title, onBack) {
        if (state.offersTypeFilter) {
            LazyRow(contentPadding = PaddingValues(horizontal = margin), horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2)) {
                items(listOf(SeerrMediaType.Movie to Strings.searchMovies, SeerrMediaType.Show to Strings.searchShows), key = { it.first }) { (type, label) ->
                    FilterChipButton(label, onClick = { viewModel.onTypeFilter(type) }, active = state.typeFilter == type)
                }
            }
        }

        when {
            state.loading -> PosterGridSkeleton(margin)

            state.message != null -> LoadError(state.message!!, viewModel::onRetry, Modifier.padding(horizontal = margin))

            else -> LazyVerticalGrid(
                // The library's grid: bigger posters on a tablet
                columns = GridCells.Adaptive(posterMinWidth(isRoot = false, wide = isTabletWidth())),
                contentPadding = PaddingValues(start = margin, top = VibeyFinTokens.Space.s3, end = margin, bottom = VibeyFinTokens.Space.s5 + LocalBottomBarPadding.current),
                horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3),
                verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s4),
            ) {
                itemsIndexed(state.titles, key = { _, title -> "${title.type}-${title.tmdbId}" }) { index, title ->
                    if (index >= state.titles.size - LOAD_MORE_BEFORE) LaunchedEffect(state.titles.size) { viewModel.onLoadMore() }
                    SeerrTile(
                        title,
                        onOpen,
                        Modifier.fillMaxWidth(),
                        meta = if (state.showsDates) Strings.seerrReleaseDay(title.releaseDate) else null,
                        plainInLibrary = true,
                    )
                }
                if (state.loadingMore) item(span = { GridItemSpan(maxLineSpan) }) { MoreSpinner() }
            }
        }
    }
}

/** My requests: every request of this profile, newest change first, filtered by state, more as it scrolls. */
@Composable
fun MyRequestsRoute(onBack: () -> Unit, onOpen: (SeerrTitle) -> Unit, viewModel: MyRequestsViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val margin = screenMargin()

    PageFrame(Strings.seerrMyRequests, onBack) {
        LazyRow(contentPadding = PaddingValues(horizontal = margin), horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2)) {
            items(RequestFilter.entries, key = { it }) { filter ->
                FilterChipButton(filter.label(), onClick = { viewModel.onFilter(filter) }, active = state.filter == filter)
            }
        }

        when {
            state.loading -> PosterGridSkeleton(margin)

            state.message != null -> LoadError(state.message!!, viewModel::onRetry, Modifier.padding(horizontal = margin))

            state.empty -> Text(
                Strings.seerrNoRequests,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = margin, vertical = VibeyFinTokens.Space.s5),
            )

            // A poster grid like the other lists: the pill says where each stands, the line under it when that last changed
            else -> LazyVerticalGrid(
                // The library's grid: bigger posters on a tablet
                columns = GridCells.Adaptive(posterMinWidth(isRoot = false, wide = isTabletWidth())),
                contentPadding = PaddingValues(start = margin, top = VibeyFinTokens.Space.s3, end = margin, bottom = VibeyFinTokens.Space.s5 + LocalBottomBarPadding.current),
                horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3),
                verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s4),
            ) {
                itemsIndexed(state.items, key = { _, item -> item.request.id }) { index, item ->
                    if (index >= state.items.size - LOAD_MORE_BEFORE) LaunchedEffect(state.items.size) { viewModel.onLoadMore() }
                    SeerrTile(item.title, onOpen, Modifier.fillMaxWidth(), meta = item.changed)
                }
                if (state.loadingMore) item(span = { GridItemSpan(maxLineSpan) }) { MoreSpinner() }
            }
        }
    }
}

private fun RequestFilter.label() = when (this) {
    RequestFilter.All -> Strings.searchAll
    RequestFilter.Waiting -> Strings.seerrFilterWaiting
    RequestFilter.Approved -> Strings.seerrFilterApproved
    RequestFilter.Available -> Strings.seerrFilterAvailable
    RequestFilter.Declined -> Strings.seerrFilterDeclined
}

@Composable
private fun PageFrame(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding(), verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2)) {
                Spacer(Modifier.height(BackButtonSpace))
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = screenMargin()).semantics { heading() },
                )
                content()
            }
            FloatingBackButton(onBack)
        }
    }
}

@Composable
private fun PosterGridSkeleton(margin: Dp) {
    LazyVerticalGrid(
        // The library's grid: bigger posters on a tablet
        columns = GridCells.Adaptive(posterMinWidth(isRoot = false, wide = isTabletWidth())),
        userScrollEnabled = false,
        contentPadding = PaddingValues(start = margin, top = VibeyFinTokens.Space.s3, end = margin),
        horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3),
        verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s4),
    ) {
        items(9) { SeerrPosterPlaceholder(Modifier.fillMaxWidth()) }
    }
}

@Composable
private fun MoreSpinner() {
    Box(Modifier.fillMaxWidth().padding(VibeyFinTokens.Space.s4), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private const val PLACEHOLDER_TILES = 4

// The next page is asked for when the list is this close to its end.
private const val LOAD_MORE_BEFORE = 6
