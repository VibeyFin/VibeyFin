package app.vibeyfin.app.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vibeyfin.R
import app.vibeyfin.app.components.Artwork
import app.vibeyfin.app.components.SectionTitle
import app.vibeyfin.app.components.TabHeader
import app.vibeyfin.app.components.VibeyFinMenu
import app.vibeyfin.app.components.VibeyFinMenuItem
import app.vibeyfin.app.navigation.LocalBottomBarPadding
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.app.theme.screenMargin
import app.vibeyfin.domain.Download
import app.vibeyfin.domain.DownloadRunner
import app.vibeyfin.domain.DownloadState
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.downloads.DownloadAction
import app.vibeyfin.ui.downloads.DownloadedShow
import app.vibeyfin.ui.downloads.DownloadsUiState
import app.vibeyfin.ui.downloads.DownloadsViewModel
import app.vibeyfin.ui.downloads.actions
import app.vibeyfin.ui.downloads.failureMessage
import app.vibeyfin.ui.downloads.isDone
import app.vibeyfin.ui.downloads.stateWord
import app.vibeyfin.ui.downloads.statusLine
import app.vibeyfin.ui.home.HomeExit
import org.koin.androidx.compose.koinViewModel

@Composable
fun DownloadsRoute(
    /** A row that is not done opens the item's page (when there is a server to ask). */
    onOpen: (itemId: String) -> Unit,
    /** A row that is done plays from the device. */
    onPlay: (itemId: String) -> Unit,
    /** The app has no server to ask: a row that is not done has no page to open. */
    offline: Boolean,
    onExit: (HomeExit) -> Unit,
    viewModel: DownloadsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.exit) { state.exit?.let(onExit) }
    DownloadsScreen(
        state = state,
        onOpen = { download ->
            if (download.isDone) {
                onPlay(download.itemId)
            } else if (!offline) {
                onOpen(download.itemId)
            }
        },
        onOpenShow = { seriesId -> if (!offline) onOpen(seriesId) },
        onAction = viewModel::onAction,
        onRemoveShow = viewModel::onRemoveShow,
        onRemoveAll = viewModel::onRemoveAll,
    )
    state.removal?.let { RemoveConfirmDialog(it, onConfirm = viewModel::onConfirmRemove, onDismiss = viewModel::onDismissRemoval) }
}

/** The Downloads tab: how much room the downloads take, the movies, and the shows with their episodes. */
@Composable
fun DownloadsScreen(
    state: DownloadsUiState,
    onOpen: (Download) -> Unit,
    onOpenShow: (seriesId: String) -> Unit,
    onAction: (Download, DownloadAction) -> Unit,
    onRemoveShow: (DownloadedShow) -> Unit,
    onRemoveAll: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = screenMargin())) {
            when {
                state.loading -> Header(state, onRemoveAll)

                state.isEmpty -> Column(Modifier.fillMaxSize()) {
                    Header(state, onRemoveAll)
                    Empty()
                }

                else -> Content(state, onOpen, onOpenShow, onAction, onRemoveShow, onRemoveAll)
            }
        }
    }
}

@Composable
private fun Header(state: DownloadsUiState, onRemoveAll: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2)) {
        TabHeader(Strings.tabDownloads, horizontalPadding = 0.dp)

        val storage = state.storageLine

        if (!state.isEmpty && !state.loading && storage != null) StorageCard(storage, onRemoveAll)
    }
}

@Composable
private fun StorageCard(storage: String, onRemoveAll: () -> Unit) {
    Surface(color = VibeyFinTokens.Colors.surface1, shape = RoundedCornerShape(VibeyFinTokens.Radius.m3Md), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = VibeyFinTokens.Space.s4, end = VibeyFinTokens.Space.s1, top = VibeyFinTokens.Space.s2, bottom = VibeyFinTokens.Space.s2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3),
        ) {
            Icon(ImageVector.vectorResource(R.drawable.ic_download), contentDescription = null, tint = VibeyFinTokens.Colors.inkMuted)
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) { }) {
                Text(Strings.downloadsStorageHeading, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(storage, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            }

            IconButton(onClick = onRemoveAll) {
                Icon(Icons.Filled.Delete, contentDescription = Strings.removeAllDownloads, tint = VibeyFinTokens.Colors.inkMuted)
            }
        }
    }
}

@Composable
private fun Empty() {
    Column(
        modifier = Modifier.fillMaxSize().padding(bottom = VibeyFinTokens.Space.s7),
        verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(Strings.downloadsEmptyTitle, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
        Text(Strings.downloadsEmptyHelp, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Content(
    state: DownloadsUiState,
    onOpen: (Download) -> Unit,
    onOpenShow: (String) -> Unit,
    onAction: (Download, DownloadAction) -> Unit,
    onRemoveShow: (DownloadedShow) -> Unit,
    onRemoveAll: () -> Unit,
) {
    // Which shows are open: kept with the screen, so coming back from an episode's page finds them as they were
    var open by rememberSaveable { mutableStateOf(listOf<String>()) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = LocalBottomBarPadding.current), verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3)) {
        item(key = "header") { Header(state, onRemoveAll) }

        if (state.movies.isNotEmpty()) {
            item(key = "movies") { SectionTitle(Strings.filterMovies, modifier = Modifier.semantics { heading() }) }
            items(state.movies, key = { "movie-" + it.itemId }) { download ->
                DownloadRow(download, state.waiting, poster = true, onOpen = { onOpen(download) }, onAction = { onAction(download, it) })
            }
        }

        if (state.shows.isNotEmpty()) {
            item(key = "shows") { SectionTitle(Strings.filterShows, modifier = Modifier.semantics { heading() }) }
            state.shows.forEach { show ->
                val expanded = show.seriesId in open

                item(key = "show-" + show.seriesId) {
                    ShowRow(
                        show = show,
                        expanded = expanded,
                        onToggle = { open = if (expanded) open - show.seriesId else open + show.seriesId },
                        onOpen = { onOpenShow(show.seriesId) },
                        onRemove = { onRemoveShow(show) },
                    )
                }

                if (expanded) {
                    items(show.episodes, key = { "episode-" + it.itemId }) { episode ->
                        Box(Modifier.padding(start = VibeyFinTokens.Space.s4)) {
                            DownloadRow(episode, state.waiting, poster = false, onOpen = { onOpen(episode) }, onAction = { onAction(episode, it) })
                        }
                    }
                }
            }
        }

        item(key = "end") { Spacer(Modifier.height(VibeyFinTokens.Space.s6)) }
    }
}

private val POSTER_WIDTH = 56.dp
private val STILL_WIDTH = 112.dp

@Composable
private fun DownloadRow(download: Download, waiting: DownloadRunner.Waiting, poster: Boolean, onOpen: () -> Unit, onAction: (DownloadAction) -> Unit) {
    val item = download.item
    val shape = RoundedCornerShape(VibeyFinTokens.Radius.artwork)
    var menu by remember { mutableStateOf(false) }
    val title = if (poster) item.title else Strings.taggedTitle(Strings.episodeTag(item.seasonNumber, item.episodeNumber), item.title)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(role = Role.Button, onClick = onOpen)
            .semantics(mergeDescendants = true) { contentDescription = Strings.downloadStateDescription(title, download.stateWord) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3),
    ) {
        val aspect = if (poster) 2f / 3f else 16f / 9f

        Box(Modifier.width(if (poster) POSTER_WIDTH else STILL_WIDTH).clip(shape)) {
            Artwork(
                url = if (poster) item.posterUrl else item.landscapeUrl ?: item.posterUrl,
                title = item.title,
                aspectRatio = aspect,
                blurHash = if (poster) item.posterBlurHash else item.landscapeBlurHash,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s1)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = VibeyFinTokens.Colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                text = download.statusLine(waiting),
                style = MaterialTheme.typography.bodyMedium,
                color = VibeyFinTokens.Colors.inkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            download.failureMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = VibeyFinTokens.Colors.danger, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }

            if (download.state == DownloadState.Running || download.state == DownloadState.Paused) {
                download.progress?.let { progress ->
                    // Ink on a quiet track: aqua is for playback progress, not for downloads
                    LinearProgressIndicator(
                        progress = { progress },
                        color = VibeyFinTokens.Colors.ink,
                        trackColor = VibeyFinTokens.Colors.progressTrack,
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                    )
                }
            }
        }

        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = Strings.showOptions, tint = VibeyFinTokens.Colors.inkMuted)
            }

            VibeyFinMenu(expanded = menu, onDismiss = { menu = false }) {
                download.actions.forEach { action ->
                    DownloadActionMenuItem(action) {
                        menu = false

                        onAction(action)
                    }
                }
            }
        }
    }
}

@Composable
private fun ShowRow(show: DownloadedShow, expanded: Boolean, onToggle: () -> Unit, onOpen: () -> Unit, onRemove: () -> Unit) {
    val shape = RoundedCornerShape(VibeyFinTokens.Radius.artwork)
    var menu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(VibeyFinTokens.Colors.surface1)
            .clickable(role = Role.Button, onClick = onToggle)
            .padding(VibeyFinTokens.Space.s2)
            .semantics(mergeDescendants = true) { contentDescription = Strings.downloadStateDescription(show.title, show.line) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3),
    ) {
        Box(Modifier.width(POSTER_WIDTH).clip(RoundedCornerShape(VibeyFinTokens.Radius.artwork))) {
            Artwork(url = show.posterUrl, title = show.title, aspectRatio = 2f / 3f, blurHash = show.posterBlurHash, modifier = Modifier.fillMaxWidth())
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s1)) {
            Text(show.title, style = MaterialTheme.typography.titleMedium, color = VibeyFinTokens.Colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(show.line, style = MaterialTheme.typography.bodyMedium, color = VibeyFinTokens.Colors.inkMuted, maxLines = 1)
        }

        Icon(
            if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = VibeyFinTokens.Colors.inkMuted,
            modifier = Modifier.size(24.dp),
        )
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = Strings.showOptions, tint = VibeyFinTokens.Colors.inkMuted)
            }

            VibeyFinMenu(expanded = menu, onDismiss = { menu = false }) {
                VibeyFinMenuItem(Strings.goToSeries(show.title), Icons.Filled.Info) {
                    menu = false

                    onOpen()
                }

                VibeyFinMenuItem(Strings.removeDownloadsOf(show.episodes.size), Icons.Filled.Delete) {
                    menu = false

                    onRemove()
                }
            }
        }
    }
}
