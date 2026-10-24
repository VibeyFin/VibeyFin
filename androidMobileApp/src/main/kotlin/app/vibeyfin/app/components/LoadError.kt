package app.vibeyfin.app.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.vibeyfin.design.VibeyFinTokens
import app.vibeyfin.ui.common.Strings

/** What went wrong and a way to try again. The button is tonal: aqua is for the primary action. */
@Composable
fun LoadError(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = VibeyFinTokens.Space.s2),
        verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3),
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
        FilledTonalButton(onClick = onRetry) { Text(Strings.tryAgain) }
    }
}
