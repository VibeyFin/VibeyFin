package app.vibeyfin.app.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import kotlinx.coroutines.launch

private const val HELD_ALPHA = 0.28f

private const val HELD_MS = 90

/**
 * Press feedback for the rows of a menu or a sheet's list (a subtitle picker, the poster menu, a dropdown): the row under the finger
 * lights up in the accent color and goes back when let go. Unlike the rest of the app a row does not shrink: a list of rows that
 * each move in under the finger looks restless, and a held row is easier to read as a color.
 */
object MenuPressIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = MenuPressNode(interactionSource)

    override fun equals(other: Any?) = other === this

    override fun hashCode() = -2
}

/** Gives every row inside [content] the accent-color press instead of the app's usual shrink. */
@Composable
fun WithMenuPress(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalIndication provides MenuPressIndication, content = content)
}

private class MenuPressNode(private val source: InteractionSource) :
    Modifier.Node(),
    DrawModifierNode {
    private val held = Animatable(0f)

    override fun onAttach() {
        coroutineScope.launch {
            source.interactions.collect { interaction ->
                val target = when (interaction) {
                    is PressInteraction.Press -> 1f
                    is PressInteraction.Release, is PressInteraction.Cancel -> 0f
                    else -> return@collect
                }

                launch { held.animateTo(target, tween(HELD_MS)) }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()

        if (held.value > 0f) drawRect(VibeyFinTokens.Colors.accent.copy(alpha = HELD_ALPHA * held.value))
    }
}
