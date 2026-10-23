package app.vibeyfin.app.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.vibeyfin.R
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.item.Trailer
import app.vibeyfin.ui.tile.TileMenuUiState

/**
 * What a long press on a poster opens: the title, then Details, the Trailer (only when the title has one: a grey line holds its place
 * while the server is asked) and My List. A trailer the server holds plays in the app; one on the web opens in the system's viewer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TileMenuSheet(
    state: TileMenuUiState,
    onDetails: () -> Unit,
    onTrailer: (Trailer) -> Unit,
    onMyList: () -> Unit,
    onDismiss: () -> Unit,
) {
    val item = state.item ?: return
    // Opens straight to its full height, with its rows already in place, instead of rising in two stages.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = VibeyFinTokens.Space.s4)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = VibeyFinTokens.Space.s4, vertical = VibeyFinTokens.Space.s2).semantics { heading() },
            )
            MenuRow(Strings.details, rememberIcon(Icons.Filled.Info), onDetails)
            // The trailer row has a slot of its own, the height of a row, kept while the server answers: a grey line fades into the row,
            // or the slot closes softly when there is none, so the buttons around it do not jump.
            androidx.compose.animation.Crossfade(
                targetState = if (state.trailer != null) 2 else if (state.checkingTrailer) 1 else 0,
                animationSpec = androidx.compose.animation.core.tween(140),
                modifier = Modifier.animateContentSize(androidx.compose.animation.core.tween(160)),
                label = "trailer row",
            ) { shown ->
                when (shown) {
                    2 -> state.trailer?.let { trailer -> MenuRow(Strings.trailer, painterIcon(R.drawable.ic_trailer)) { onTrailer(trailer) } }
                    1 -> Row(Modifier.fillMaxWidth().heightIn(min = ROW_HEIGHT).padding(horizontal = VibeyFinTokens.Space.s4), verticalAlignment = Alignment.CenterVertically) {
                        SkeletonLine(120.dp, MaterialTheme.typography.bodyLarge)
                    }
                    else -> Unit
                }
            }
            // A change that could not be saved was taken back; the sheet says so.
            state.actionMessage?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = VibeyFinTokens.Space.s4, vertical = VibeyFinTokens.Space.s2).semantics { liveRegion = LiveRegionMode.Polite })
            }
            MenuRow(Strings.myListAction(item.isFavorite), rememberIcon(if (item.isFavorite) Icons.Filled.Check else Icons.Filled.Add), onMyList)
        }
    }
}

private val ROW_HEIGHT = 56.dp

private class MenuIcon(val vector: ImageVector? = null, val resource: Int? = null)

private fun rememberIcon(vector: ImageVector) = MenuIcon(vector = vector)

private fun painterIcon(resource: Int) = MenuIcon(resource = resource)

@Composable
private fun MenuRow(label: String, icon: MenuIcon, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = ROW_HEIGHT).clickable(role = Role.Button, onClick = onClick).padding(horizontal = VibeyFinTokens.Space.s4),
        horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = MaterialTheme.colorScheme.onSurfaceVariant
        icon.vector?.let { Icon(it, contentDescription = null, tint = tint) }
        icon.resource?.let { Icon(painterResource(it), contentDescription = null, tint = tint) }
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}
