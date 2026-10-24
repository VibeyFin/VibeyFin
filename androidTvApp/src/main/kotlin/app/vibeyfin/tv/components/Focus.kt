package app.vibeyfin.tv.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import app.vibeyfin.design.VibeyFinTokens

private val FocusShape = RoundedCornerShape(12.dp)

@Composable
fun FocusSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(FocusShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = VibeyFinTokens.Colors.surface2,
            focusedContainerColor = VibeyFinTokens.Colors.surface3,
        ),
        // No scale on focus: it re-lays-out the picture on every move and lags on TV boxes.
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(3.dp, VibeyFinTokens.Colors.focus), shape = FocusShape),
        ),
        content = content,
    )
}
