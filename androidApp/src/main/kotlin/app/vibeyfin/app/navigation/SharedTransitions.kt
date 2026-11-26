@file:OptIn(ExperimentalSharedTransitionApi::class)

package app.vibeyfin.app.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.vibeyfin.app.components.LocalSkeleton

/**
 * The shared-element transition from a poster to the detail page's picture, with Compose's own `SharedTransitionLayout` (no new
 * dependency). The layout wraps the NavHost and hands its scope down here; each destination hands down its own animation scope.
 */
internal val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

internal val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Which item's poster was tapped last. Only that one tile takes part, so an item that sits in several rows (My List, Recently added,
 * the hero) never has two tiles claiming the same element; the detail page's picture is its other end, and on the way back the
 * same tile is the landing place.
 */
object SharedPick {
    var itemId: String? by mutableStateOf(null)
}

/** A destination that hands its animation scope down, so posters and pictures in it can take part in the shared transition. */
internal inline fun <reified T : Any> NavGraphBuilder.sharedComposable(
    crossinline content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) = composable<T> { entry ->
    CompositionLocalProvider(LocalNavAnimatedScope provides this) { content(entry) }
}

private const val SHARED_FADE_MS = 140

// Off: a detail page fades in whole instead of flying the poster across; the shared-transition code stays for switching it back on
private const val SHARED_POSTER_ENABLED = false

/**
 * Marks this as one end of [itemId]'s poster-to-picture transition. [source] is the poster in a list: it takes part only when it is the
 * one last tapped. The detail page's picture passes `source = false` and always takes part. Outside a shared layout, or while
 * [SHARED_POSTER_ENABLED] is off, it does nothing.
 */
@Composable
fun Modifier.sharedPoster(itemId: String, source: Boolean): Modifier {
    if (!SHARED_POSTER_ENABLED || LocalSkeleton.current) return this

    val transition = LocalSharedTransitionScope.current ?: return this
    val animated = LocalNavAnimatedScope.current ?: return this

    if (source && SharedPick.itemId != itemId) return this
    return with(transition) {
        this@sharedPoster.sharedBounds(
            sharedContentState = rememberSharedContentState(key = "poster-$itemId"),
            animatedVisibilityScope = animated,
            enter = fadeIn(tween(SHARED_FADE_MS)),
            exit = fadeOut(tween(SHARED_FADE_MS)),
            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.Crop),
        )
    }
}
