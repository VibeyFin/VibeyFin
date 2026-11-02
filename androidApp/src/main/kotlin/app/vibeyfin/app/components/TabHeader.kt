package app.vibeyfin.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import app.vibeyfin.app.navigation.LocalProfileButton
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.app.theme.screenMargin
import app.vibeyfin.ui.common.Strings

/**
 * The header of the four tabs and the sign-in pages: the page name at the left and the profile at the right, one line, the same
 * size and in the same place on every page, so it stays still when the page changes. [actions] sit before [trailing], which is the
 * profile on a tab and the server on "Who's watching?". [horizontalPadding] is 0 inside a screen that already has the margin.
 */
@Composable
fun TabHeader(
    title: String,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = screenMargin(),
    actions: @Composable RowScope.() -> Unit = {},
    trailing: @Composable () -> Unit = LocalProfileButton.current,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = VibeyFinTokens.Space.s2)
            .heightIn(min = VibeyFinTokens.Size.touch),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        actions()
        trailing()
    }
}

/** The round button at the right of the header that opens a menu: the profile's letter, or the server. [modifier] names it. */
@Composable
fun HeaderButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .size(VibeyFinTokens.Size.touch)
            .clip(CircleShape)
            .background(VibeyFinTokens.Colors.surface3)
            .clickable(role = Role.Button, onClickLabel = Strings.showOptions, onClick = onClick)
            .then(modifier),
        contentAlignment = Alignment.Center,
        content = content,
    )
}
