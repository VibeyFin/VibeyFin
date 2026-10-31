package app.vibeyfin.app.seerr

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vibeyfin.R
import app.vibeyfin.app.components.FloatingBackButton
import app.vibeyfin.app.components.ItemSkeleton
import app.vibeyfin.app.components.LoadError
import app.vibeyfin.app.components.SectionTitle
import app.vibeyfin.app.item.ActionButton
import app.vibeyfin.app.item.ActionRow
import app.vibeyfin.app.item.ItemBackdrop
import app.vibeyfin.app.item.Overview
import app.vibeyfin.app.item.PinnedBackdrop
import app.vibeyfin.app.navigation.LocalBottomBarPadding
import app.vibeyfin.app.navigation.LocalOpenTrailerUrl
import app.vibeyfin.app.navigation.LocalSeerrLongPress
import app.vibeyfin.app.theme.BrandDisplay
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.app.theme.screenMargin
import app.vibeyfin.domain.model.RequestState
import app.vibeyfin.domain.model.SeerrCastMember
import app.vibeyfin.domain.model.SeerrMediaType
import app.vibeyfin.domain.model.SeerrTitle
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.seerr.SeerrNoticeAction
import app.vibeyfin.ui.seerr.SeerrSeasonRow
import app.vibeyfin.ui.seerr.SeerrTitleUiState
import app.vibeyfin.ui.seerr.SeerrTitleViewModel
import coil3.compose.AsyncImage
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The page of a title that isn't in the library: the detail page's hero and layout, with Seerr's status and one action. A title the
 * library has opens the library's page instead ([onOpenItem], which takes this page's place).
 */
@Composable
fun SeerrTitleRoute(
    type: SeerrMediaType,
    tmdbId: Int,
    onBack: () -> Unit,
    onOpenItem: (String) -> Unit,
    onOpenSeerr: (SeerrTitle) -> Unit,
    viewModel: SeerrTitleViewModel = koinViewModel(parameters = { parametersOf(type, tmdbId) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    val openTrailer = LocalOpenTrailerUrl.current

    LaunchedEffect(state.openItem) {
        state.openItem?.let {
            viewModel.onItemOpened()
            onOpenItem(it)
        }
    }

    // On this page "View" would lead here, so only Try again is offered
    LaunchedEffect(state.notice) {
        val notice = state.notice ?: return@LaunchedEffect

        viewModel.onNoticeShown()

        val retry = notice.action == SeerrNoticeAction.TryAgain
        val result = snackbars.showSnackbar(notice.text, actionLabel = if (retry) notice.actionLabel else null, duration = SnackbarDuration.Short)

        if (retry && result == SnackbarResult.ActionPerformed) viewModel.onRetryRequest()
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= WIDE_FROM
            val margin = if (wide) VibeyFinTokens.Space.s6 else screenMargin()

            when {
                state.loading && state.title == null -> ItemSkeleton(wide = wide, screenWidth = maxWidth, screenHeight = maxHeight, margin = margin)
                state.title == null -> LoadError(state.message.orEmpty(), viewModel::onRetry, Modifier.padding(horizontal = margin).padding(top = 96.dp))
                else -> SeerrTitlePage(state, wide, maxWidth, maxHeight, margin, viewModel, openTrailer, onOpenItem, onOpenSeerr)
            }

            FloatingBackButton(onBack)
            SnackbarHost(snackbars, Modifier.align(Alignment.BottomCenter).padding(bottom = LocalBottomBarPadding.current))
        }
    }

    state.picker?.let { picker ->
        SeerrSeasonPickerSheet(
            picker = picker,
            sending = state.sending,
            onAllMissing = viewModel::onPickerAllMissing,
            onSeason = viewModel::onPickerSeason,
            onQuality = viewModel::onPickerQuality,
            onConfirm = viewModel::onPickerConfirm,
            onDismiss = viewModel::onPickerDismiss,
        )
    }
}

@Composable
private fun SeerrTitlePage(
    state: SeerrTitleUiState,
    wide: Boolean,
    screenWidth: Dp,
    screenHeight: Dp,
    margin: Dp,
    viewModel: SeerrTitleViewModel,
    openTrailer: (String) -> Unit,
    onOpenItem: (String) -> Unit,
    onOpenSeerr: (SeerrTitle) -> Unit,
) {
    val title = state.title ?: return
    val scroll = rememberScrollState()
    val picture = title.backdropUrl ?: title.posterUrl
    val sections: @Composable () -> Unit = {
        Column(Modifier.padding(horizontal = margin, vertical = VibeyFinTokens.Space.s5), verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s5)) {
            if (state.seasons.isNotEmpty()) SeasonList(state.seasons)
            if (state.cast.isNotEmpty()) CastRow(state.cast)
            if (state.moreLikeThis.isNotEmpty()) MoreLikeThis(state.moreLikeThis, onOpenItem, onOpenSeerr)
            Spacer(Modifier.height(VibeyFinTokens.Space.s5))
        }
    }

    if (!wide) {
        val heroHeight = screenWidth * PHONE_HERO_BY_WIDTH

        Box(Modifier.fillMaxSize()) {
            PinnedBackdrop(title.title, picture, null, heroHeight, scroll)
            Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                Box(Modifier.fillMaxWidth().heightIn(min = heroHeight)) {
                    Column(Modifier.align(Alignment.BottomCenter).padding(horizontal = margin).padding(bottom = VibeyFinTokens.Space.s4)) {
                        Hero(state, centered = true, viewModel, openTrailer)
                    }
                }
                Box(Modifier.fillMaxWidth().background(VibeyFinTokens.Colors.bg).padding(bottom = LocalBottomBarPadding.current)) { sections() }
            }
        }

        return
    }

    val firstScreenPx = with(LocalDensity.current) { (screenHeight * FIRST_SCREEN).toPx() }
    val progress = if (firstScreenPx > 0f) scroll.value / firstScreenPx else 0f

    Box(Modifier.fillMaxSize()) {
        ItemBackdrop(title.title, picture, null, screenWidth, screenHeight, progress, Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            Spacer(Modifier.height(screenHeight * FIRST_SCREEN_CLEAR))
            Column(Modifier.fillMaxWidth(0.46f).widthIn(max = 520.dp).padding(horizontal = margin)) { Hero(state, centered = false, viewModel, openTrailer) }
            sections()
            Spacer(Modifier.height(LocalBottomBarPadding.current))
        }
    }
}

// The title (no Logo from Seerr, so the brand face), the facts, the overview, the status line, the one primary button and the actions
@Composable
private fun Hero(state: SeerrTitleUiState, centered: Boolean, viewModel: SeerrTitleViewModel, openTrailer: (String) -> Unit) {
    val title = state.title ?: return
    val align = if (centered) TextAlign.Center else TextAlign.Start

    Column(
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(title.title, style = BrandDisplay.copy(fontSize = 32.sp, lineHeight = 36.sp), color = VibeyFinTokens.Colors.ink, textAlign = align, maxLines = 3, overflow = TextOverflow.Ellipsis)

        if (state.facts.isNotEmpty()) Text(Strings.factsLine(state.facts), style = MaterialTheme.typography.bodyMedium, color = VibeyFinTokens.Colors.inkMuted, textAlign = align)
        if (state.genres.isNotEmpty()) Text(Strings.factsLine(state.genres), style = MaterialTheme.typography.bodyMedium, color = VibeyFinTokens.Colors.inkMuted, textAlign = align)

        state.overview?.let { Overview(it) }

        state.status?.let { StatusLine(it, state.state) }
        state.status4k?.let { StatusLine(it, state.state) }

        state.primary?.let { primary ->
            // The one aqua button. When there is nothing to do it stays, quiet and disabled, so the page doesn't jump
            Button(
                onClick = viewModel::onRequest,
                enabled = primary.enabled,
                modifier = Modifier.fillMaxWidth().height(VibeyFinTokens.Size.buttonLarge),
            ) {
                if (state.sending && state.picker == null) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(primary.label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
        }

        if (state.trailerUrl != null || state.offer4k || state.cancelRequestId != null) {
            ActionRow(Modifier.fillMaxWidth()) {
                state.trailerUrl?.let { url ->
                    ActionButton(Strings.trailer, Strings.trailer, icon = { Icon(painterResource(R.drawable.ic_trailer), null, tint = VibeyFinTokens.Colors.ink, modifier = it) }) { openTrailer(url) }
                }
                if (state.offer4k) {
                    ActionButton(Strings.seerrRequest4k, Strings.seerrRequest4k, icon = { Icon(Icons.Filled.Add, null, tint = VibeyFinTokens.Colors.ink, modifier = it) }, onClick = viewModel::onRequest4k)
                }
                if (state.cancelRequestId != null) {
                    ActionButton(Strings.seerrCancelRequest, Strings.seerrCancelRequest, icon = { Icon(Icons.Filled.Close, null, tint = VibeyFinTokens.Colors.ink, modifier = it) }, onClick = viewModel::onCancelRequest)
                }
            }
        }
    }
}

/** Where the title stands, as an icon and words in ink (a failure in `danger`); never aqua. */
@Composable
internal fun StatusLine(text: String, state: RequestState) {
    val color = if (state == RequestState.Failed) VibeyFinTokens.Colors.danger else VibeyFinTokens.Colors.ink

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2)) {
        if (state == RequestState.NotRequested) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        } else {
            Icon(painterResource(stateIconOf(state)), contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

@Composable
private fun SeasonList(seasons: List<SeerrSeasonRow>) {
    Column(verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2)) {
        SectionTitle(Strings.seerrSeasons, modifier = Modifier.semantics { heading() })
        seasons.forEach { season ->
            Row(Modifier.fillMaxWidth().padding(vertical = VibeyFinTokens.Space.s1), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(season.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
                    Text(
                        listOfNotNull(season.episodes, season.year?.toString()).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                season.stateLabel?.let { StatusLine(it, season.state) }
            }
        }
    }
}

@Composable
private fun CastRow(cast: List<SeerrCastMember>) {
    Column(verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2)) {
        SectionTitle(Strings.cast, modifier = Modifier.semantics { heading() })
        // Not tappable: these people have no page in the library
        LazyRow(horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3)) {
            items(cast, key = { it.id }) { person ->
                Column(Modifier.width(CAST_TILE), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s1)) {
                    Box(Modifier.size(CAST_TILE).clip(CircleShape).background(VibeyFinTokens.Colors.surface2), contentAlignment = Alignment.Center) {
                        Text(Strings.initials(person.name), style = MaterialTheme.typography.titleMedium, color = VibeyFinTokens.Colors.inkMuted)
                        person.photoUrl?.let { AsyncImage(model = it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
                    }
                    Text(person.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onBackground, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
                    person.character?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MoreLikeThis(titles: List<SeerrTitle>, onOpenItem: (String) -> Unit, onOpenSeerr: (SeerrTitle) -> Unit) {
    val longPress = LocalSeerrLongPress.current

    Column(verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2)) {
        SectionTitle(Strings.seerrMoreLikeThis, modifier = Modifier.semantics { heading() })
        LazyRow(horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3), contentPadding = PaddingValues(0.dp)) {
            items(titles, key = { "${it.type}-${it.tmdbId}" }) { title ->
                SeerrPosterTile(
                    title,
                    Modifier.width(VibeyFinTokens.Size.posterWidth).clip(RoundedCornerShape(VibeyFinTokens.Radius.m3Md)).combinedClickable(
                        role = Role.Button,
                        onLongClickLabel = Strings.showOptions,
                        onLongClick = longPress?.let { press -> { press(title) } },
                        // A title the library has opens the library's page
                        onClick = { title.jellyfinItemId?.let(onOpenItem) ?: onOpenSeerr(title) },
                    ),
                )
            }
        }
    }
}

internal fun stateIconOf(state: RequestState): Int = when (state) {
    RequestState.Requested -> R.drawable.ic_seerr_requested
    RequestState.Approved -> R.drawable.ic_seerr_approved
    RequestState.PartlyAvailable -> R.drawable.ic_seerr_partly
    RequestState.Available -> R.drawable.ic_seerr_available
    RequestState.Declined -> R.drawable.ic_seerr_declined
    RequestState.Failed, RequestState.NotRequested -> R.drawable.ic_seerr_failed
}

private val WIDE_FROM = 600.dp
private const val PHONE_HERO_BY_WIDTH = 1.5f
private const val FIRST_SCREEN = 0.7f
private const val FIRST_SCREEN_CLEAR = 0.3f
private val CAST_TILE = 72.dp
