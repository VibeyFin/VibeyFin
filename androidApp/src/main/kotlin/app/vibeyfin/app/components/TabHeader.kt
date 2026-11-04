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
import androidx.compose.ui.graphics.Color
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
 * The header of the four tabs: the page name at the left and the profile at the right, one line, the same size and in the same
 * place on every tab, so it stays still when the tab changes. [actions] sit before the profile. [horizontalPadding] is 0 inside a
 * screen that already has the margin.
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

@Composable
fun HeaderButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = VibeyFinTokens.Colors.surface3,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .size(VibeyFinTokens.Size.touch)
            .clip(CircleShape)
            .background(background)
            .clickable(role = Role.Button, onClickLabel = Strings.showOptions, onClick = onClick)
            .then(modifier),
        contentAlignment = Alignment.Center,
        content = content,
    )
}
