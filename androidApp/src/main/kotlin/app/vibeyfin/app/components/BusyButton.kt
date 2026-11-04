package app.vibeyfin.app.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.vibeyfin.app.theme.VibeyFinTokens

@Composable
fun BusyButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    busyLabel: String? = null,
    enabled: Boolean = true,
) {
    Button(
        onClick = { if (!busy) onClick() },
        enabled = enabled || busy,
        modifier = modifier.fillMaxWidth().height(VibeyFinTokens.Size.buttonLarge),
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            busyLabel?.let {
                Spacer(Modifier.size(VibeyFinTokens.Space.s3))
                Text(it)
            }
        } else {
            Text(label)
        }
    }
}
