package app.vibeyfin.app.whoswatching

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.lerp
import app.vibeyfin.domain.model.Profile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val ZOOM_SCALE = 1.8f
private const val GROW_DAMPING = 0.55f
private const val FLY_AFTER_MS = 300L
private const val FLY_MS = 450
private val FlyEasing = CubicBezierEasing(0.6f, 0f, 0.2f, 1f)

private const val TILE_CORNER_PERCENT = 12
private const val CIRCLE_PERCENT = 50

/**
 * The picture of the profile that signed in, flying from its tile to the profile button. It outlives "Who's watching?" so Home can
 * fade in underneath while it flies.
 */
@Stable
class SignInFlight(private val scope: CoroutineScope) {
    var profile by mutableStateOf<Profile?>(null)
        private set

    val grow = Animatable(0f)
    val fly = Animatable(0f)

    private var from = Rect.Zero
    private var screen = Rect.Zero
    private var size: Dp = Dp.Unspecified

    private var serverButton = Rect.Zero
    private var homeButton by mutableStateOf(Rect.Zero)

    /** Home's profile button: it says where it is, and waits invisible until the picture has landed on it. */
    fun profileButtonModifier() = Modifier
        .onGloballyPositioned {
            val bounds = Rect(it.positionInRoot(), it.size.toSize())
            if (screen.overlaps(bounds)) homeButton = bounds
        }
        .graphicsLayer { alpha = if (profile != null) 0f else 1f }

    fun start(profile: Profile, from: Rect, size: Dp, screen: Rect, serverButton: Rect, enterHome: () -> Unit) {
        this.from = from
        this.size = size
        this.screen = screen
        this.serverButton = serverButton
        this.homeButton = Rect.Zero

        scope.launch {
            grow.snapTo(0f)
            fly.snapTo(0f)
            this@SignInFlight.profile = profile

            coroutineScope {
                launch { grow.animateTo(1f, spring(GROW_DAMPING, Spring.StiffnessMedium)) }
                delay(FLY_AFTER_MS)
                enterHome()
                fly.animateTo(1f, tween(FLY_MS, easing = FlyEasing))
            }
            this@SignInFlight.profile = null
        }
    }

    @Composable
    fun Overlay() {
        val flying = profile ?: return
        var origin by remember { mutableStateOf<Offset?>(null) }

        Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
            Box(
                Modifier
                    .offset { origin?.let { IntOffset((from.left - it.x).roundToInt(), (from.top - it.y).roundToInt()) } ?: IntOffset.Zero }
                    .graphicsLayer {
                        alpha = if (origin == null) 0f else 1f

                        val button = homeButton.takeIf { it != Rect.Zero } ?: serverButton.takeIf { it != Rect.Zero } ?: Rect(screen.center, 0f)
                        val g = grow.value
                        val f = fly.value
                        val middle = screen.center - from.center
                        val end = button.center - from.center
                        val endScale = if (button.width > 0f) button.width / from.width else ZOOM_SCALE
                        translationX = lerp(middle.x * g, end.x, f)
                        translationY = lerp(middle.y * g, end.y, f)
                        scaleX = lerp(lerp(1f, ZOOM_SCALE, g), endScale, f)
                        scaleY = scaleX
                        shape = RoundedCornerShape(lerp(TILE_CORNER_PERCENT.toFloat(), CIRCLE_PERCENT.toFloat(), f).roundToInt())
                        clip = true
                    },
            ) {
                Avatar(flying, size)
            }
        }
    }
}

@Composable
fun rememberSignInFlight(): SignInFlight {
    val scope = rememberCoroutineScope()
    return remember { SignInFlight(scope) }
}
