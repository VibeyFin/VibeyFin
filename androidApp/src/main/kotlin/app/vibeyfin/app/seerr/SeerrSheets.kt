package app.vibeyfin.app.seerr

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import app.vibeyfin.R
import app.vibeyfin.app.components.Artwork
import app.vibeyfin.app.components.SkeletonLine
import app.vibeyfin.app.components.VibeyFinSheet
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.domain.model.SeerrTitle
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.seerr.SeerrNotice
import app.vibeyfin.ui.seerr.SeerrSeasonPicker
import app.vibeyfin.ui.seerr.SeerrTitleUiState
import app.vibeyfin.ui.seerr.SeerrTitleViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/** The season picker a show's Request opens: All missing seasons, 4K when allowed, each season, and one aqua button. */
@Composable
fun SeerrSeasonPickerSheet(
    picker: SeerrSeasonPicker,
    sending: Boolean,
    onAllMissing: (Boolean) -> Unit,
    onSeason: (Int) -> Unit,
    onQuality: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    VibeyFinSheet(onDismiss) { SeasonPickerContent(picker, sending, onAllMissing, onSeason, onQuality, onConfirm) }
}

@Composable
private fun ColumnScope.SeasonPickerContent(
    picker: SeerrSeasonPicker,
    sending: Boolean,
    onAllMissing: (Boolean) -> Unit,
    onSeason: (Int) -> Unit,
    onQuality: (Boolean) -> Unit,
    onConfirm: () -> Unit,
) {
    Column(Modifier.navigationBarsPadding().padding(bottom = VibeyFinTokens.Space.s4).verticalScroll(rememberScrollState())) {
        Column(Modifier.padding(horizontal = VibeyFinTokens.Space.s5, vertical = VibeyFinTokens.Space.s2)) {
            Text(picker.title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.semantics { heading() })
            Text(picker.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (picker.offer4k) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = VibeyFinTokens.Space.s5, vertical = VibeyFinTokens.Space.s2)) {
                listOf(false to Strings.seerrStandard, true to Strings.seerr4k).forEachIndexed { index, (is4k, label) ->
                    SegmentedButton(
                        selected = picker.is4k == is4k,
                        onClick = { onQuality(is4k) },
                        shape = SegmentedButtonDefaults.itemShape(index, 2),
                        colors = SegmentedButtonDefaults.colors(activeContainerColor = VibeyFinTokens.Colors.accentSoft, activeContentColor = VibeyFinTokens.Colors.ink),
                    ) { Text(label) }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().toggleable(value = picker.allMissing, role = Role.Switch, onValueChange = onAllMissing).padding(horizontal = VibeyFinTokens.Space.s5, vertical = VibeyFinTokens.Space.s3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(Strings.seerrAllMissingSeasons, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            // Aqua is for the one primary action, so the switch is light on dark
            Switch(
                checked = picker.allMissing,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(checkedTrackColor = VibeyFinTokens.Colors.ink, checkedThumbColor = VibeyFinTokens.Colors.bg),
            )
        }
        HorizontalDivider(Modifier.padding(horizontal = VibeyFinTokens.Space.s5), color = MaterialTheme.colorScheme.outlineVariant)

        picker.seasons.forEach { season ->
            Row(
                Modifier.fillMaxWidth()
                    .toggleable(value = season.selected, enabled = !season.locked, role = Role.Checkbox, onValueChange = { onSeason(season.number) })
                    .padding(horizontal = VibeyFinTokens.Space.s4, vertical = VibeyFinTokens.Space.s1),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = season.selected, onCheckedChange = null, enabled = !season.locked)
                Spacer(Modifier.width(VibeyFinTokens.Space.s3))
                Column(Modifier.weight(1f)) {
                    Text(season.name, style = MaterialTheme.typography.bodyLarge, color = if (season.locked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                    Text(season.episodes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                season.stateLabel?.let { StatusLine(it, season.state) }
            }
        }

        Button(
            onClick = onConfirm,
            enabled = picker.canConfirm || sending,
            modifier = Modifier.fillMaxWidth().padding(horizontal = VibeyFinTokens.Space.s5, vertical = VibeyFinTokens.Space.s3).height(VibeyFinTokens.Size.buttonLarge),
        ) {
            if (sending) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text(picker.confirmLabel)
            }
        }
    }
}

/**
 * The menu a long press on a Seerr poster opens. It has a ViewModel of its own for as long as it is open. A request ends it: the
 * message about it goes to [onNotice], which the screen under it shows. A show's Request swaps the menu for its season picker.
 */
@Composable
fun SeerrMenuSheet(title: SeerrTitle, onDetails: () -> Unit, onTrailer: (String) -> Unit, onNotice: (SeerrNotice) -> Unit, onDismiss: () -> Unit) {
    val owner = remember(title) {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }

    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }

    val viewModel: SeerrTitleViewModel = koinViewModel(viewModelStoreOwner = owner, key = "seerr-menu", parameters = { parametersOf(title.type, title.tmdbId) })
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.notice) {
        val notice = state.notice ?: return@LaunchedEffect

        viewModel.onNoticeShown()
        onNotice(notice)
        onDismiss()
    }

    VibeyFinSheet(onDismiss) {
        val picker = state.picker

        if (picker != null) {
            SeasonPickerContent(picker, state.sending, viewModel::onPickerAllMissing, viewModel::onPickerSeason, viewModel::onPickerQuality, viewModel::onPickerConfirm)
        } else {
            MenuContent(title, state, viewModel, onDetails, onTrailer)
        }
    }
}

@Composable
private fun MenuContent(title: SeerrTitle, state: SeerrTitleUiState, viewModel: SeerrTitleViewModel, onDetails: () -> Unit, onTrailer: (String) -> Unit) {
    Column(Modifier.navigationBarsPadding().padding(bottom = VibeyFinTokens.Space.s4)) {
        Row(
            Modifier.padding(horizontal = VibeyFinTokens.Space.s4, vertical = VibeyFinTokens.Space.s2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3),
        ) {
            Box(Modifier.width(40.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(6.dp))) {
                Artwork(url = title.posterUrl, title = title.title, aspectRatio = 2f / 3f, modifier = Modifier.fillMaxWidth())
            }
            Column {
                Text(title.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
                Text(
                    state.menuSubtitle.ifEmpty { Strings.seerrMenuSubtitle(Strings.seerrTitleMeta(title.year, title.type), title.state) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = VibeyFinTokens.Space.s4), color = MaterialTheme.colorScheme.outlineVariant)

        if (state.loading) {
            // Every row comes at once, so none appears late under a finger
            repeat(3) { Row(Modifier.fillMaxWidth().heightIn(min = ROW_HEIGHT).padding(horizontal = VibeyFinTokens.Space.s4), verticalAlignment = Alignment.CenterVertically) { SkeletonLine(140.dp, MaterialTheme.typography.bodyLarge) } }
            return@Column
        }

        // Someone asked for it: where it stands is a row of its own, not a button
        state.menuStatus?.let { status ->
            Row(Modifier.fillMaxWidth().heightIn(min = ROW_HEIGHT).padding(horizontal = VibeyFinTokens.Space.s4), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s4)) {
                Icon(painterResource(stateIconOf(state.state)), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Column {
                    Text(status, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    state.menuStatusDetail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }

        if (state.canRequest) {
            MenuRow(state.primary?.label ?: Strings.seerrRequest, Icons.Filled.Add, viewModel::onRequest)
            if (state.offer4k) MenuRow(Strings.seerrRequest4k, Icons.Filled.Add, viewModel::onRequest4k)
        }
        MenuRow(Strings.details, Icons.Filled.Info, onDetails)
        state.trailerUrl?.let { url -> MenuRow(Strings.trailer, null, { onTrailer(url) }, R.drawable.ic_trailer) }
        if (state.cancelRequestId != null) MenuRow(Strings.seerrCancelRequest, Icons.Filled.Close, viewModel::onCancelRequest)
    }
}

@Composable
private fun MenuRow(label: String, icon: ImageVector?, onClick: () -> Unit, resource: Int? = null) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = ROW_HEIGHT).clickable(role = Role.Button, onClick = onClick).padding(horizontal = VibeyFinTokens.Space.s4),
        horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = MaterialTheme.colorScheme.onSurfaceVariant

        icon?.let { Icon(it, contentDescription = null, tint = tint) }
        resource?.let { Icon(painterResource(it), contentDescription = null, tint = tint) }
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

private val ROW_HEIGHT = 56.dp
