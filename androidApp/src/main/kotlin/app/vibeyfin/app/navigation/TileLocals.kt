package app.vibeyfin.app.navigation

import androidx.compose.runtime.compositionLocalOf

/** What a long press on a poster does: the nav host opens the poster's menu. Null outside it, where a long press does nothing. */
val LocalTileLongPress = compositionLocalOf<((app.vibeyfin.domain.model.MediaItem) -> Unit)?> { null }

/** Opens a trailer on the web straight in YouTube (or the system's viewer for another site). Set by the nav host. */
val LocalOpenTrailerUrl = compositionLocalOf<(String) -> Unit> { {} }
