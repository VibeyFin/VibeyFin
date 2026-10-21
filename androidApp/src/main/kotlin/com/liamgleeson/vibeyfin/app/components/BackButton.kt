package com.liamgleeson.vibeyfin.app.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.liamgleeson.vibeyfin.app.theme.VibeyFinTokens
import com.liamgleeson.vibeyfin.app.theme.screenMargin
import com.liamgleeson.vibeyfin.ui.common.Strings

/**
 * An arrow on a round [ChipButton] over the content, with no label; it has a name for screen readers.
 * Screens show it through [FloatingBackButton], not in a row of their own.
 */
@Composable
fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    ChipButton(rememberVectorPainter(Icons.AutoMirrored.Filled.ArrowBack), Strings.back, onClick, modifier)
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
        modifier = Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(start = screenMargin() - ChipInset, top = VibeyFinTokens.Space.s2),
    )
}

/** The height at the top of a screen's content kept clear for the button: 8 dp above it, 48 dp of it, 8 dp below. */
val BackButtonSpace = VibeyFinTokens.Size.touch + VibeyFinTokens.Space.s2 * 2
