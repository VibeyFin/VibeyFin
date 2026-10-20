package com.liamgleeson.vibeyfin.app.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.ripple
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.DrawModifierNode
import kotlinx.coroutines.launch

/** How far a pressed button, card or chip shrinks. Small: it should feel like pressing, not like a bounce. */
private const val PRESSED_SCALE = 0.95f

/**
 * The app's press feedback: the ripple the system draws, and under the finger the pressed thing shrinks a
 * little and springs back when let go. It is the theme's indication, so every Material button, card, chip
 * and plain `clickable` gets it without doing anything; only what is drawn scales, never the touch area.
 */
object PressScaleIndication : IndicationNodeFactory {
    private val ripple = ripple()

    override fun create(interactionSource: InteractionSource): DelegatableNode =
        PressScaleNode(interactionSource, ripple.create(interactionSource))

    override fun equals(other: Any?) = other === this

    override fun hashCode() = -1
}

private class PressScaleNode(
    private val source: InteractionSource,
    rippleNode: DelegatableNode,
) : DelegatingNode(), DrawModifierNode {
    private val scale = Animatable(1f)

    init {
        delegate(rippleNode)
    }

    override fun onAttach() {
        coroutineScope.launch {
            source.interactions.collect { interaction ->
                val target = if (interaction is PressInteraction.Press) PRESSED_SCALE else if (interaction.endsAPress()) 1f else return@collect
                // Each change takes over from the one before, so a quick tap never leaves it shrunk.
                launch { scale.animateTo(target, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium)) }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        scale(scale.value) { this@draw.drawContent() }
    }
}

private fun androidx.compose.foundation.interaction.Interaction.endsAPress() =
    this is PressInteraction.Release || this is PressInteraction.Cancel
