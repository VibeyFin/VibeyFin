package app.vibeyfin.app.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.ui.common.Strings

/**
 * The bottom sheet every picker and menu uses. It opens straight to its full height instead of rising in two stages,
 * so rows are already in place when it appears.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VibeyFinSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        content = content,
    )
}

@Composable
fun SheetColumn(
    onCancel: (() -> Unit)?,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    footnote: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = VibeyFinTokens.Space.s5, vertical = VibeyFinTokens.Space.s2),
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s4),
    ) {
        content()
        onCancel?.let { SheetTextButton(Strings.cancel, it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        footnote?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(VibeyFinTokens.Space.s2))
    }
}

@Composable
fun SheetTextButton(label: String, onClick: () -> Unit, color: Color = MaterialTheme.colorScheme.onSurface) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(VibeyFinTokens.Size.touch)) {
        Text(label, color = color)
    }
}

@Composable
fun SheetTitle(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
}
