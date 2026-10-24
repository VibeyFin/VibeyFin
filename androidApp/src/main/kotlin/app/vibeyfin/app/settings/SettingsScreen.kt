package app.vibeyfin.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vibeyfin.app.components.BackButtonSpace
import app.vibeyfin.app.components.FloatingBackButton
import app.vibeyfin.app.theme.ContentMaxWidth
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.app.theme.screenMargin
import app.vibeyfin.domain.ConnectionManager
import app.vibeyfin.domain.ConnectionState
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.home.HomeExit
import app.vibeyfin.ui.settings.ActionItem
import app.vibeyfin.ui.settings.ChoiceItem
import app.vibeyfin.ui.settings.CustomItem
import app.vibeyfin.ui.settings.CustomKind
import app.vibeyfin.ui.settings.InfoItem
import app.vibeyfin.ui.settings.LinkItem
import app.vibeyfin.ui.settings.SettingItem
import app.vibeyfin.ui.settings.SettingKey
import app.vibeyfin.ui.settings.SettingsPage
import app.vibeyfin.ui.settings.SettingsPageId
import app.vibeyfin.ui.settings.SettingsViewModel
import app.vibeyfin.ui.settings.ToggleItem
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/** One page of the Settings tree, from its id; one the tree does not have (yet) shows only the back button. */
@Composable
fun SettingsRoute(
    pageId: SettingsPageId,
    onOpen: (SettingsPageId) -> Unit,
    onExit: (HomeExit) -> Unit,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
    connection: ConnectionManager = koinInject(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LeaveWhenDisconnected(connection, onExit)
    val page = state.pages[pageId]
    if (page == null) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { Box(Modifier.fillMaxSize()) { FloatingBackButton(onBack) } }
    } else {
        SettingsPageScreen(
            page = page,
            hasPage = { it in state.pages },
            onToggle = viewModel::onToggle,
            onChoose = viewModel::onChoose,
            onAction = viewModel::onAction,
            onOpen = onOpen,
            onBack = onBack,
        )
    }
    if (state.confirmSignOut) {
        AlertDialog(
            onDismissRequest = viewModel::onDismissSignOut,
            title = { Text(Strings.signOutConfirmTitle) },
            text = { Text(Strings.signOutConfirmMessage) },
            confirmButton = { TextButton(viewModel::onConfirmSignOut) { Text(Strings.signOut, color = VibeyFinTokens.Colors.danger) } },
            dismissButton = { TextButton(viewModel::onDismissSignOut, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) { Text(Strings.cancel) } },
        )
    }
}

/**
 * Switch profile and Sign out end the connection here, where nothing else is watching it: go to
 * "Who's watching?" of the server that was signed in to, or to Servers when it is not known.
 */
@Composable
private fun LeaveWhenDisconnected(connection: ConnectionManager, onExit: (HomeExit) -> Unit) {
    val connectionState by connection.state.collectAsStateWithLifecycle()
    var serverId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(connectionState) {
        when (val state = connectionState) {
            is ConnectionState.Connected -> serverId = state.server.id
            ConnectionState.Disconnected -> serverId?.let { onExit(HomeExit.WhoIsWatching(it)) }
            is ConnectionState.Expired -> Unit
        }
    }
}

/** A page drawn from its [SettingsPage]: sections of rows. */
@Composable
fun SettingsPageScreen(
    page: SettingsPage,
    onToggle: (SettingKey, Boolean) -> Unit,
    onChoose: (SettingKey, Int) -> Unit,
    onAction: (SettingKey) -> Unit,
    onOpen: (SettingsPageId) -> Unit,
    onBack: () -> Unit,
    hasPage: (SettingsPageId) -> Boolean = { true },
) {
    val drawn = page.drawn(hasPage)
    val preview = page.subtitlePreviewStyle()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(BackButtonSpace))
                Column(
                    modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(horizontal = screenMargin()),
                    verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2),
                ) {
                    Text(
                        text = drawn.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(bottom = VibeyFinTokens.Space.s3),
                    )
                    drawn.sections.forEachIndexed { index, section ->
                        if (index > 0) Spacer(Modifier.height(VibeyFinTokens.Space.s3))
                        section.title?.let { SectionHeader(it) }
                        val standalone = section.items.singleOrNull()?.takeIf { it is CustomItem && it.kind == CustomKind.SubtitlePreview }
                        if (standalone != null) {
                            // The preview is a card of its own, not a row of a group's card.
                            SubtitlePreviewCard(standalone.title, preview)
                        } else {
                            SectionCard {
                                section.items.forEach { item ->
                                    // Keyed by page and key: a key can sit on two pages, such as Buffer ahead.
                                    key(rowId(page.id, item)) { ItemRow(page, item, onToggle, onChoose, onAction, onOpen) }
                                }
                            }
                        }
                        section.footer?.let { SectionFooter(it) }
                    }
                    Spacer(Modifier.height(VibeyFinTokens.Space.s5))
                }
            }
            FloatingBackButton(onBack)
        }
    }
}

@Composable
private fun ItemRow(
    page: SettingsPage,
    item: SettingItem,
    onToggle: (SettingKey, Boolean) -> Unit,
    onChoose: (SettingKey, Int) -> Unit,
    onAction: (SettingKey) -> Unit,
    onOpen: (SettingsPageId) -> Unit,
) {
    when (item) {
        is ToggleItem -> ToggleRow(item) { onToggle(item.key, it) }
        // A checklist choice draws its options inline in its own section: the section's title is its title.
        is ChoiceItem -> if (item.asPage) {
            item.checklist().forEach { (option, selected) -> ChecklistRow(option, selected) { onChoose(item.key, option.value) } }
        } else {
            ChoiceRow(item) { onChoose(item.key, it) }
        }
        is LinkItem -> LinkRow(item) { onOpen(item.target) }
        is ActionItem -> ActionRow(item) { onAction(item.key) }
        is InfoItem -> InfoRow(item)
        is CustomItem -> when (item.kind) {
            CustomKind.ProfileHeader -> ProfileHeaderRow(item.title, item.help)
            // A preview that is not alone in its section sits in the card as a row.
            CustomKind.SubtitlePreview -> SubtitlePreviewCard(item.title, page.subtitlePreviewStyle())
            // The PIN dots belong to the PIN screens, which are not in the tree yet.
            CustomKind.PinDots -> Unit
        }
    }
}
