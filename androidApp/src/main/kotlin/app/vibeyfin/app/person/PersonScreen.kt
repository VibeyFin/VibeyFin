package app.vibeyfin.app.person

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vibeyfin.app.components.ArtworkTile
import app.vibeyfin.app.components.BackButtonSpace
import app.vibeyfin.app.components.FloatingBackButton
import app.vibeyfin.app.components.LoadError
import app.vibeyfin.app.components.MediaRowSection
import app.vibeyfin.app.components.POSTER_CAPTION_LINES
import app.vibeyfin.app.components.SectionTitle
import app.vibeyfin.app.components.SkeletonBlock
import app.vibeyfin.app.components.SkeletonLine
import app.vibeyfin.app.components.skeletonCount
import app.vibeyfin.app.components.skeletonTiles
import app.vibeyfin.app.navigation.LocalBottomBarPadding
import app.vibeyfin.app.theme.BrandDisplay
import app.vibeyfin.app.theme.ContentMaxWidth
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.app.theme.pageInsetsPadding
import app.vibeyfin.app.theme.screenMargin
import app.vibeyfin.domain.model.MediaItem
import app.vibeyfin.domain.model.Person
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.home.HomeExit
import app.vibeyfin.ui.home.MediaRow
import app.vibeyfin.ui.person.PersonUiState
import app.vibeyfin.ui.person.PersonViewModel
import coil3.compose.AsyncImage
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

private val WIDE_FROM: Dp = 600.dp
private val POSTER_WIDE: Dp = 168.dp
private val PORTRAIT_SIZE: Dp = 160.dp
private const val BIOGRAPHY_LINES = 4
private const val BIOGRAPHY_FOLDS_FROM = 180
private const val SKELETON_TILES = 6

@Composable
fun PersonRoute(
    personId: String,
    onBack: () -> Unit,
    onOpen: (MediaItem) -> Unit,
    onExit: (HomeExit) -> Unit,
    viewModel: PersonViewModel = koinViewModel(parameters = { parametersOf(personId) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.exit) {
        state.exit?.let(onExit)
    }

    PersonScreen(state, onBack, onOpen, onRetry = viewModel::onRetry)
}

/** A cast member's page: a round portrait, the name, where and when they were born, the biography, then what they appear in. */
@Composable
fun PersonScreen(state: PersonUiState, onBack: () -> Unit, onOpen: (MediaItem) -> Unit, onRetry: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (maxWidth >= WIDE_FROM) PersonGrid(state, onOpen, onRetry) else PersonColumn(state, onOpen, onRetry)

            FloatingBackButton(onBack)
        }
    }
}

@Composable
private fun PersonColumn(state: PersonUiState, onOpen: (MediaItem) -> Unit, onRetry: () -> Unit) {
    val margin = screenMargin()

    Column(
        modifier = Modifier.fillMaxSize().pageInsetsPadding().verticalScroll(rememberScrollState()).padding(bottom = LocalBottomBarPadding.current),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(BackButtonSpace))

        Column(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s4)) {
            when {
                state.loading -> PersonSkeleton()

                state.message != null -> LoadError(state.message!!, onRetry, modifier = Modifier.padding(horizontal = margin))

                state.person != null -> {
                    PersonHeader(state, margin)

                    Column(Modifier.padding(horizontal = margin), verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s5)) {
                        state.movies.takeIf { it.isVisible }?.let { MediaRowSection(it, margin, onOpen, onSeeAll = null, onRetry = onRetry) }
                        state.shows.takeIf { it.isVisible }?.let { MediaRowSection(it, margin, onOpen, onSeeAll = null, onRetry = onRetry) }
                        state.appearancesMessage?.let { LoadError(it, onRetry) }

                        if (state.noAppearances) NoAppearances()
                    }
                }
            }

            Spacer(Modifier.height(VibeyFinTokens.Space.s5))
        }
    }
}

@Composable
private fun PersonGrid(state: PersonUiState, onOpen: (MediaItem) -> Unit, onRetry: () -> Unit) {
    val margin = screenMargin()
    val skeletonTileCount = skeletonCount(SKELETON_TILES)
    val bottomSpace = LocalBottomBarPadding.current + VibeyFinTokens.Space.s5

    LazyVerticalGrid(
        columns = GridCells.Adaptive(POSTER_WIDE),
        modifier = Modifier.fillMaxSize().pageInsetsPadding(),
        contentPadding = PaddingValues(start = margin, end = margin, bottom = bottomSpace),
        verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s4),
        horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3),
    ) {
        fullWidth("back-space") { Spacer(Modifier.height(BackButtonSpace)) }

        when {
            state.loading -> fullWidth("skeleton") { PersonSkeletonWide() }

            state.message != null -> fullWidth("error") { LoadError(state.message!!, onRetry) }

            state.person != null -> {
                fullWidth("header") { PersonHeaderWide(state) }
                filmography(state.movies, skeletonTileCount, onOpen, onRetry)
                filmography(state.shows, skeletonTileCount, onOpen, onRetry)
                state.appearancesMessage?.let { message -> fullWidth("appearances-error") { LoadError(message, onRetry) } }

                if (state.noAppearances) fullWidth("no-appearances") { NoAppearances() }
            }
        }
    }
}

private fun LazyGridScope.fullWidth(key: String, content: @Composable LazyGridItemScope.() -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }, content = content)
}

private fun LazyGridScope.filmography(row: MediaRow, skeletonTileCount: Int, onOpen: (MediaItem) -> Unit, onRetry: () -> Unit) {
    if (!row.isVisible) return

    fullWidth("${row.id}-title") { SectionTitle(row.title, maxLines = 1, modifier = Modifier.padding(top = VibeyFinTokens.Space.s3).semantics { heading() }) }

    when {
        row.loading -> skeletonTiles(count = skeletonTileCount, keyPrefix = "${row.id}-skeleton")

        row.message != null -> fullWidth("${row.id}-error") { LoadError(row.message.orEmpty(), onRetry) }

        else -> items(row.items.distinctBy { it.id }, key = { "${row.id}-${it.id}" }, contentType = { "poster" }) { item ->
            ArtworkTile(item, onClick = { onOpen(item) }, modifier = Modifier.animateItem(), captionLines = POSTER_CAPTION_LINES)
        }
    }
}

@Composable
private fun NoAppearances() {
    Text(Strings.noAppearances, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun PersonHeader(state: PersonUiState, margin: Dp) {
    val person = state.person ?: return

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = margin),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2),
    ) {
        Portrait(person)
        Spacer(Modifier.height(VibeyFinTokens.Space.s2))
        PersonDetails(state, person, centered = true)
    }
}

@Composable
private fun PersonHeaderWide(state: PersonUiState) {
    val person = state.person ?: return

    Row(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s5)) {
        Portrait(person)
        Box(Modifier.weight(1f).padding(top = VibeyFinTokens.Space.s2)) { PersonDetails(state, person, centered = false) }
    }
}

@Composable
private fun Portrait(person: Person) {
    Box(Modifier.size(PORTRAIT_SIZE).clip(CircleShape).background(VibeyFinTokens.Colors.surface2), contentAlignment = Alignment.Center) {
        Text(
            Strings.initials(person.name),
            style = BrandDisplay.copy(fontSize = 44.sp),
            color = VibeyFinTokens.Colors.inkMuted,
            // The portrait is named by the picture and the heading; the initials would be read as a stray word
            modifier = Modifier.clearAndSetSemantics { },
        )

        if (person.imageUrl != null) {
            AsyncImage(model = person.imageUrl, contentDescription = person.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun PersonDetails(state: PersonUiState, person: Person, centered: Boolean) {
    val textAlign = if (centered) TextAlign.Center else TextAlign.Start
    val biography = person.overview
    // Saveable: on a tablet the header is a grid item, which forgets plain state when it scrolls out of view
    var expanded by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2),
    ) {
        Text(
            person.name,
            style = BrandDisplay.copy(fontSize = 32.sp, lineHeight = 36.sp),
            color = VibeyFinTokens.Colors.ink,
            textAlign = textAlign,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )

        if (state.facts.isNotEmpty()) {
            Text(Strings.factsLine(state.facts), style = MaterialTheme.typography.titleSmall, color = VibeyFinTokens.Colors.inkMuted, textAlign = textAlign)
        }

        Text(
            text = biography ?: Strings.noBiography,
            style = MaterialTheme.typography.bodyMedium,
            color = if (biography != null) VibeyFinTokens.Colors.ink else VibeyFinTokens.Colors.inkMuted,
            textAlign = textAlign,
            maxLines = if (expanded) Int.MAX_VALUE else BIOGRAPHY_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(top = VibeyFinTokens.Space.s2)
                .clickable(enabled = biography != null, role = Role.Button, onClickLabel = if (expanded) Strings.less else Strings.more) { expanded = !expanded }
                .animateContentSize(),
        )

        if (biography != null && biography.length > BIOGRAPHY_FOLDS_FROM) {
            Text(
                text = if (expanded) Strings.less else Strings.more,
                style = MaterialTheme.typography.labelLarge,
                color = VibeyFinTokens.Colors.inkMuted,
                modifier = Modifier.clickable { expanded = !expanded }.padding(vertical = VibeyFinTokens.Space.s1),
            )
        }
    }
}

@Composable
private fun PersonSkeleton() {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3)) {
        SkeletonBlock(Modifier.size(PORTRAIT_SIZE), CircleShape)
        SkeletonDetailLines()
    }
}

@Composable
private fun PersonSkeletonWide() {
    Row(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s5)) {
        SkeletonBlock(Modifier.size(PORTRAIT_SIZE), CircleShape)
        Column(Modifier.padding(top = VibeyFinTokens.Space.s2), verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3)) { SkeletonDetailLines() }
    }
}

@Composable
private fun SkeletonDetailLines() {
    SkeletonLine(200.dp, MaterialTheme.typography.headlineMedium)
    SkeletonLine(140.dp, MaterialTheme.typography.titleSmall)
    SkeletonLine(280.dp, MaterialTheme.typography.bodyMedium)
    SkeletonLine(240.dp, MaterialTheme.typography.bodyMedium)
}
