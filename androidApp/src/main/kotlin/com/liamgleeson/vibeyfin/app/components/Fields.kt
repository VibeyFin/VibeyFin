package com.liamgleeson.vibeyfin.app.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable

/**
 * Text fields focus in the text colour. Material's default is the primary colour, and amber is
 * reserved for the single primary action on a screen.
 */
@Composable
fun vibeyFinTextFieldColors(): TextFieldColors {
    val scheme = MaterialTheme.colorScheme
    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor = scheme.onSurface,
        unfocusedBorderColor = scheme.outlineVariant,
        focusedLabelColor = scheme.onSurface,
        unfocusedLabelColor = scheme.onSurfaceVariant,
        cursorColor = scheme.onSurface,
        focusedPlaceholderColor = scheme.onSurfaceVariant,
        unfocusedPlaceholderColor = scheme.onSurfaceVariant,
    )
}
