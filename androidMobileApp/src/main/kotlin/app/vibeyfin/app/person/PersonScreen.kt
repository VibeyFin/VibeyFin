package app.vibeyfin.app.person

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
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
import app.vibeyfin.app.components.BackButtonSpace
import app.vibeyfin.app.components.FloatingBackButton
import app.vibeyfin.app.components.LoadError
import app.vibeyfin.app.components.MediaRowSection
import app.vibeyfin.app.components.SkeletonBlock
import app.vibeyfin.app.components.SkeletonLine
import app.vibeyfin.app.navigation.LocalBottomBarPadding
import app.vibeyfin.app.theme.ContentMaxWidth
import app.vibeyfin.app.theme.screenMargin
import app.vibeyfin.design.BrandDisplay
import app.vibeyfin.design.VibeyFinTokens
import app.vibeyfin.domain.model.MediaItem
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.home.HomeExit
import app.vibeyfin.ui.person.PersonUiState
import app.vibeyfin.ui.person.PersonViewModel
import coil3.compose.AsyncImage
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

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
    val margin = screenMargin()
    val wide = LocalConfiguration.current.screenWidthDp >= 600

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(bottom = LocalBottomBarPadding.current),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(BackButtonSpace))

                Column(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s4)) {
                    when {
                        state.loading -> PersonSkeleton()

                        state.message != null -> LoadError(state.message!!, onRetry, modifier = Modifier.padding(horizontal = margin))

                        state.person != null -> {
                            PersonHeader(state, margin)

                            val tile = if (wide) 168.dp else VibeyFinTokens.Size.posterWidth

                            Column(Modifier.padding(horizontal = margin), verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s5)) {
                                state.movies.takeIf { it.isVisible }?.let { MediaRowSection(it, margin, onOpen, onSeeAll = null, onRetry = onRetry, tileWidth = tile) }
                                state.shows.takeIf { it.isVisible }?.let { MediaRowSection(it, margin, onOpen, onSeeAll = null, onRetry = onRetry, tileWidth = tile) }
                                state.appearancesMessage?.let { LoadError(it, onRetry) }

                                if (state.noAppearances) {
                                    Text(Strings.noAppearances, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(VibeyFinTokens.Space.s5))
                }
            }

            FloatingBackButton(onBack)
        }
    }
}

@Composable
private fun PersonHeader(state: PersonUiState, margin: Dp) {
    val person = state.person ?: return
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = margin),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2),
    ) {
        Box(Modifier.size(160.dp).clip(CircleShape).background(VibeyFinTokens.Colors.surface2), contentAlignment = Alignment.Center) {
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

        Spacer(Modifier.height(VibeyFinTokens.Space.s2))
        Text(
            person.name,
            style = BrandDisplay.copy(fontSize = 32.sp, lineHeight = 36.sp),
            color = VibeyFinTokens.Colors.ink,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )

        if (state.facts.isNotEmpty()) {
            Text(Strings.factsLine(state.facts), style = MaterialTheme.typography.titleSmall, color = VibeyFinTokens.Colors.inkMuted, textAlign = TextAlign.Center)
        }

        val bio = person.overview

        Text(
            text = bio ?: Strings.noBiography,
            style = MaterialTheme.typography.bodyMedium,
            color = if (bio != null) VibeyFinTokens.Colors.ink else VibeyFinTokens.Colors.inkMuted,
            textAlign = TextAlign.Center,
            maxLines = if (expanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = VibeyFinTokens.Space.s2).clickable(enabled = bio != null, role = Role.Button, onClickLabel = if (expanded) Strings.less else Strings.more) { expanded = !expanded }.animateContentSize(),
        )

        if (bio != null && bio.length > 180) {
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
        SkeletonBlock(Modifier.size(160.dp), CircleShape)
        SkeletonLine(200.dp, MaterialTheme.typography.headlineMedium)
        SkeletonLine(140.dp, MaterialTheme.typography.titleSmall)
        SkeletonLine(280.dp, MaterialTheme.typography.bodyMedium)
        SkeletonLine(240.dp, MaterialTheme.typography.bodyMedium)
    }
}
