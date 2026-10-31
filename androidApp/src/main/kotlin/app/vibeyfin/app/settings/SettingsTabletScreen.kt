package app.vibeyfin.app.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.vibeyfin.app.components.BackButtonSpace
import app.vibeyfin.app.components.FloatingBackButton
import app.vibeyfin.app.theme.screenMargin
import app.vibeyfin.ui.settings.SettingKey
import app.vibeyfin.ui.settings.SettingsPageId
import app.vibeyfin.ui.settings.SettingsUiState
import org.koin.androidx.compose.koinViewModel

private val ListPaneWidth = 380.dp

private val DetailMaxWidth = 640.dp

/**
 * Settings at tablet width: the grouped root on the left, the chosen page on the right, drawn by the same
 * rows as the phone. Tapping a link on the root selects that row (and shows its page) instead of navigating.
 * The selected row is saved across rotation and falls back to the first link to a page with something to set.
 */
@Composable
fun SettingsTabletScreen(
    state: SettingsUiState,
    onToggle: (SettingKey, Boolean) -> Unit,
    onChoose: (SettingKey, Int) -> Unit,
    onAction: (SettingKey) -> Unit,
    onBack: () -> Unit,
    onPageOpened: (SettingsPageId) -> Unit = {},
    onOpen: (SettingsPageId) -> Unit = {},
) {
    // The open row is kept, not its page: two root rows can lead to the same page
    var saved by rememberSaveable { mutableStateOf<SettingKey?>(null) }
    val selected = selectedLink(saved, state.pages)
    val root = state.pages[SettingsPageId.Root]
    val detail = selected?.let { state.pages[it.target] }
    val hasPage: (SettingsPageId) -> Boolean = { it in state.pages }
    val margin = screenMargin()

    LaunchedEffect(detail?.id) { detail?.id?.let(onPageOpened) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxSize().safeDrawingPadding()) {
                Column(Modifier.width(ListPaneWidth).fillMaxHeight().verticalScroll(rememberScrollState())) {
                    Spacer(Modifier.height(BackButtonSpace))

                    if (root != null) {
                        SettingsPageBody(
                            root,
                            hasPage,
                            Modifier.fillMaxWidth().padding(horizontal = margin),
                            selectedLink = selected?.key,
                            onToggle,
                            onChoose,
                            onAction,
                            onOpen = { saved = it.key },
                        )
                    }
                }

                VerticalDivider(Modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.outlineVariant)
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(BackButtonSpace))

                    if (detail?.id == SettingsPageId.Seerr) {
                        // Seerr's page is its own screen, drawn here beside the list
                        SeerrSettingsContent(
                            koinViewModel(),
                            Modifier.widthIn(max = DetailMaxWidth).fillMaxWidth().padding(horizontal = margin),
                            onLeave = { saved = null },
                        )
                    } else if (detail != null) {
                        // Keyed by page so scroll and menus do not carry over from the page before
                        key(detail.id) {
                            SettingsPageBody(
                                detail,
                                hasPage,
                                Modifier.widthIn(max = DetailMaxWidth).fillMaxWidth().padding(horizontal = margin),
                                // A link inside the page (a language list) opens its picker sheet over both panes
                                selectedLink = null,
                                onToggle,
                                onChoose,
                                onAction,
                                onOpen = { onOpen(it.target) },
                            )
                        }
                    }
                }
            }

            FloatingBackButton(onBack)
        }
    }
}
