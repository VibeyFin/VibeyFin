package app.vibeyfin.app.seerr

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.vibeyfin.app.components.Artwork
import app.vibeyfin.app.components.SkeletonBlock
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.domain.model.RequestState
import app.vibeyfin.domain.model.SeerrTitle
import app.vibeyfin.ui.common.Strings

/**
 * A Seerr title as a poster tile: the artwork with its marker in the badge slot (top-left), the title on up to two lines and
 * "2025 · Movie" under it. Read as one item, with what Seerr knows of it.
 */
@Composable
fun SeerrPosterTile(
    title: SeerrTitle,
    modifier: Modifier = Modifier,
    /** Under the title instead of "2025 · Movie", such as Coming soon's "Dec 12". */
    meta: String? = null,
    /** A title the library has is drawn as the library's own tile, with no Seerr marker (the discover rows). */
    plainInLibrary: Boolean = false,
) {
    val meta = meta ?: Strings.seerrTitleMeta(title.year, title.type)
    val marked = !(plainInLibrary && title.jellyfinItemId != null)

    Column(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = Strings.seerrTitleDescription(title.title, meta, title.state) },
        verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2),
    ) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(VibeyFinTokens.Radius.artwork))) {
            Artwork(url = title.posterUrl, title = title.title, aspectRatio = 2f / 3f, modifier = Modifier.fillMaxWidth())
            if (marked) SeerrMarker(title.state, Modifier.align(Alignment.TopStart).padding(VibeyFinTokens.Space.s2))
        }

        Column(verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s1 / 2)) {
            // Always two lines' room, so a short title and a long one make tiles of one height and rows never change size
            Text(title.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * What Seerr knows about a title, in the poster's one badge slot: a + disc for a title nobody asked for, otherwise a pill with an
 * icon and a word. Ink on `scrim`, never aqua (aqua never signals status); a failure is in `danger`. The tile's own description
 * says it, so the marker is not read on its own.
 */
@Composable
fun SeerrMarker(state: RequestState, modifier: Modifier = Modifier) {
    val label = Strings.seerrStateLabel(state)

    if (label == null) {
        Box(modifier.size(26.dp).clip(CircleShape).background(VibeyFinTokens.Colors.scrim), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = VibeyFinTokens.Colors.ink, modifier = Modifier.size(16.dp))
        }

        return
    }

    val color = if (state == RequestState.Failed) VibeyFinTokens.Colors.danger else VibeyFinTokens.Colors.ink

    Row(
        modifier.height(22.dp).clip(RoundedCornerShape(VibeyFinTokens.Radius.artwork)).background(VibeyFinTokens.Colors.scrim).padding(horizontal = VibeyFinTokens.Space.s2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s1),
    ) {
        Icon(painterResource(stateIconOf(state)), contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
    }
}

/** A grey stand-in for [SeerrPosterTile], laid out like it (the same picture and text lines), so the row keeps its height when the real tiles arrive. */
@Composable
fun SeerrPosterPlaceholder(modifier: Modifier = Modifier) {
    Column(modifier.clearAndSetSemantics { }, verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2)) {
        SkeletonBlock(Modifier.fillMaxWidth().aspectRatio(2f / 3f))
        Column(verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s1 / 2)) {
            Text("", style = MaterialTheme.typography.titleSmall, minLines = 2, maxLines = 2)
            Text("", style = MaterialTheme.typography.bodySmall, minLines = 1, maxLines = 1)
        }
    }
}
