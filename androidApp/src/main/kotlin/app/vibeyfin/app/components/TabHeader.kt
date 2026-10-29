package app.vibeyfin.app.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import app.vibeyfin.app.navigation.LocalProfileButton
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.app.theme.screenMargin

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
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = VibeyFinTokens.Space.s2),
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
        LocalProfileButton.current()
    }
}
