package app.vibeyfin.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vibeyfin.app.components.BackButtonSpace
import app.vibeyfin.app.components.DangerConfirmDialog
import app.vibeyfin.app.components.FloatingBackButton
import app.vibeyfin.app.components.OptionRow
import app.vibeyfin.app.components.PickerSearch
import app.vibeyfin.app.components.PickerSheet
import app.vibeyfin.app.navigation.LocalBottomBarPadding
import app.vibeyfin.app.theme.ContentMaxWidth
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.app.theme.pageInsetsPadding
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

/** One page of the Settings tree, from its id (or both panes of it when [twoPane]); one the tree does not have (yet) shows only the back button. */
@Composable
fun SettingsRoute(
    pageId: SettingsPageId,
    onOpen: (SettingsPageId) -> Unit,
    onExit: (HomeExit) -> Unit,
    onBack: () -> Unit,
    twoPane: Boolean = false,
    viewModel: SettingsViewModel = koinViewModel(),
    connection: ConnectionManager = koinInject(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LeaveWhenDisconnected(connection, onExit)

    LaunchedEffect(state.leave) { if (state.leave) onBack() }

    var sheet by rememberSaveable { mutableStateOf<SettingsPageId?>(null) }
    val open: (SettingsPageId) -> Unit = { id ->
        if (state.pages[id]?.sheet == true) {
            viewModel.onPageOpened(id)

            sheet = id
        } else {
            onOpen(id)
        }
    }

    LaunchedEffect(state.closePage) {
        val closing = state.closePage ?: return@LaunchedEffect

        viewModel.onPageClosed()

        if (closing == sheet) sheet = null
    }

    NotificationPermissionRequest(
        requested = state.requestNotificationPermission,
        onRequested = viewModel::onNotificationPermissionRequested,
        onDenied = viewModel::onNotificationPermissionDenied,
    )

    // The Kids and PIN page reads the server's limits whenever it is shown (on a tablet, when its row is selected)
    val page = state.pages[pageId]

    if (!twoPane) LaunchedEffect(pageId, state.locked) { if (!state.locked) viewModel.onPageOpened(pageId) }
    if (state.locked) {
        // Nothing of Settings shows until the PIN is right, or is found not needed; a spinner, so the wait never reads as an empty page
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).semantics { contentDescription = Strings.loadingPage },
                )
                FloatingBackButton(onBack)
            }
        }
    } else if (twoPane) {
        SettingsTabletScreen(
            state = state,
            onToggle = viewModel::onToggle,
            onChoose = viewModel::onChoose,
            onAction = viewModel::onAction,
            onBack = onBack,
            onPageOpened = viewModel::onPageOpened,
            onOpen = open,
        )
    } else if (page == null) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { Box(Modifier.fillMaxSize()) { FloatingBackButton(onBack) } }
    } else {
        SettingsPageScreen(
            page = page,
            hasPage = { it in state.pages },
            onToggle = viewModel::onToggle,
            onChoose = viewModel::onChoose,
            onAction = viewModel::onAction,
            onOpen = open,
            onBack = onBack,
        )
    }

    sheet?.let { id -> state.pages[id]?.let { page -> SettingsPickerSheet(page, viewModel::onChooseLanguage, viewModel::onSearch) { sheet = null } } }
    state.pinPrompt?.let { prompt ->
        PinSheet(
            prompt = prompt,
            onSubmitPin = viewModel::onSubmitPin,
            onForgotPin = viewModel::onForgotPin,
            onSubmitPassword = viewModel::onSubmitPassword,
            onDismiss = viewModel::onDismissPin,
        )
    }

    if (state.confirmPurgeDownloads) {
        DangerConfirmDialog(
            title = Strings.purgeDownloadsConfirmTitle,
            message = Strings.purgeDownloadsConfirmMessage,
            confirmLabel = Strings.deleteFiles,
            onConfirm = viewModel::onConfirmPurgeDownloads,
            onDismiss = viewModel::onDismissPurgeDownloads,
        )
    }

    if (state.confirmSignOut) {
        DangerConfirmDialog(
            title = Strings.signOutConfirmTitle,
            message = Strings.signOutConfirmMessage,
            confirmLabel = Strings.signOut,
            onConfirm = viewModel::onConfirmSignOut,
            onDismiss = viewModel::onDismissSignOut,
        )
    }
}

// Nothing else watches the connection here: Switch profile and Sign out go to "Who's watching?" of the
// server signed in to, or to Servers when it is not known
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
    val content = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(horizontal = screenMargin())

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().pageInsetsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(BackButtonSpace))
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = LocalBottomBarPadding.current),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SettingsPageBody(page, hasPage, content, selectedLink = null, onToggle, onChoose, onAction, onOpen = { onOpen(it.target) })
                }
            }

            FloatingBackButton(onBack)
        }
    }
}

/**
 * The title and sections of a page, shared by the phone's page and the tablet's two panes. [selectedLink]
 * is the row whose page is open beside this list on a tablet; null on a phone.
 */
@Composable
internal fun SettingsPageBody(
    page: SettingsPage,
    hasPage: (SettingsPageId) -> Boolean,
    modifier: Modifier,
    selectedLink: SettingKey?,
    onToggle: (SettingKey, Boolean) -> Unit,
    onChoose: (SettingKey, Int) -> Unit,
    onAction: (SettingKey) -> Unit,
    onOpen: (LinkItem) -> Unit,
) {
    val drawn = page.drawn(hasPage)
    val preview = page.subtitlePreviewStyle()

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2)) {
        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = VibeyFinTokens.Space.s3),
        )
        drawn.sections.forEachIndexed { index, section ->
            if (index > 0) Spacer(Modifier.height(VibeyFinTokens.Space.s3))

            section.title?.let { SectionHeader(it) }

            val standalone = section.items.singleOrNull()?.takeIf { it is CustomItem && it.kind == CustomKind.SubtitlePreview }

            if (standalone != null) {
                SubtitlePreviewCard(standalone.title, preview)
            } else {
                SectionCard {
                    section.items.forEach { item ->
                        // Keyed by page and key, so a key that ever sits on two pages stays distinct
                        key(rowId(page.id, item)) { ItemRow(item, selectedLink, onToggle, onChoose, onAction, onOpen) }
                    }
                }
            }

            section.footer?.let { SectionFooter(it) }
        }

        Spacer(Modifier.height(VibeyFinTokens.Space.s5))
    }
}

// Picking saves the option; the ViewModel then asks for the sheet to close
@Composable
private fun SettingsPickerSheet(
    page: SettingsPage,
    onChooseLanguage: (SettingKey, String) -> Unit,
    onSearch: (SettingsPageId, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val items = page.visibleOnly().sections.flatMap { it.items }
    val choice = items.filterIsInstance<ChoiceItem>().firstOrNull()
    val rows = remember(choice) {
        choice?.options.orEmpty().map { OptionRow(key = it.code.orEmpty(), label = it.label, selected = it.value == choice?.value) }
    }

    PickerSheet(
        title = page.title,
        rows = rows,
        loading = false,
        message = null,
        emptyText = if (rows.isEmpty()) items.filterIsInstance<InfoItem>().firstOrNull()?.title else null,
        onPick = { code -> choice?.let { onChooseLanguage(it.key, code) } },
        onDismiss = onDismiss,
        onRetry = {},
        search = page.search?.let { query -> PickerSearch(query, page.searchPrompt.orEmpty()) { onSearch(page.id, it) } },
    )
}

@Composable
private fun ItemRow(
    item: SettingItem,
    selectedLink: SettingKey?,
    onToggle: (SettingKey, Boolean) -> Unit,
    onChoose: (SettingKey, Int) -> Unit,
    onAction: (SettingKey) -> Unit,
    onOpen: (LinkItem) -> Unit,
) {
    when (item) {
        is ToggleItem -> ToggleRow(item) { onToggle(item.key, it) }

        // A checklist choice draws its options inline in its own section: the section's title is its title
        is ChoiceItem -> if (item.asPage) {
            item.checklist().forEach { (option, selected) -> ChecklistRow(option, selected) { onChoose(item.key, option.value) } }
        } else {
            ChoiceRow(item) { onChoose(item.key, it) }
        }

        is LinkItem -> LinkRow(item, selected = selectedLink?.let { it == item.key }) { onOpen(item) }

        is ActionItem -> ActionRow(item) { onAction(item.key) }

        is InfoItem -> InfoRow(item)

        is CustomItem -> when (item.kind) {
            CustomKind.ProfileHeader -> ProfileHeaderRow(item.title, item.help)

            // The shared builder gives the preview a section of its own (drawn above), so no row; listed to keep the when exhaustive
            CustomKind.SubtitlePreview -> Unit

            CustomKind.PinDots -> PinDotsItemRow(item.title, item.help)
        }
    }
}
