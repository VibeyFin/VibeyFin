package com.liamgleeson.vibeyfin.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.liamgleeson.vibeyfin.app.theme.VibeyFinPalette

/**
 * An arrow on a round chip like the profile button on Home, with no label; it has a name for
 * screen readers. Screens show it through [FloatingBackButton], not in a row of their own.
 */
@Composable
fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    // 48 dp to touch, 40 dp to look at.
    Box(
        modifier = modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .background(VibeyFinPalette.SurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = VibeyFinPalette.Text,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * The back button hovering over the content in the top-left corner, always visible. It is drawn on
 * top rather than placed in a row, so it takes no room of its own: content scrolls underneath it.
 * Put it last in the [Box] that fills the screen, and start the screen's content with
 * [BackButtonSpace] so the title begins beneath the button instead of behind it.
 */
@Composable
fun BoxScope.FloatingBackButton(onClick: () -> Unit) {
    BackButton(
        onClick = onClick,
        modifier = Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(start = 20.dp, top = 8.dp),
    )
}

/** The height at the top of a screen's content kept clear for the button: 8 dp above it, 48 dp of it, 8 dp below. */
val BackButtonSpace = 64.dp
