package app.vibeyfin.app.navigation

import androidx.compose.runtime.compositionLocalOf
import app.vibeyfin.domain.model.MediaItem
import app.vibeyfin.domain.model.SeerrTitle

/** What a long press on a poster does: the nav host opens the poster's menu. Null outside it, where a long press does nothing. */
val LocalTileLongPress = compositionLocalOf<((MediaItem) -> Unit)?> { null }

/** What a long press on a Seerr poster does: the nav host opens the Seerr poster menu. Null outside it. */
val LocalSeerrLongPress = compositionLocalOf<((SeerrTitle) -> Unit)?> { null }

/** Opens a trailer on the web straight in YouTube (or the system's viewer for another site). Set by the nav host. */
val LocalOpenTrailerUrl = compositionLocalOf<(String) -> Unit> { {} }
