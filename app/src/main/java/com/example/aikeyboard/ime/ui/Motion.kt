package com.example.aikeyboard.ime.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/*
 * The motion language of the promo video (promo/promo.html): things pop in on a soft spring, one after
 * another; headlines rise into view from behind a clip line; pressed things sink a little.
 */

/** Entrance: scale up from [from] and fade in on a spring, [index] × [stepMs] after the screen appears. */
fun Modifier.popIn(index: Int = 0, stepMs: Long = 35, from: Float = 0.85f, lift: Dp = 0.dp): Modifier = composed {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * stepMs)
        p.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 380f))
    }
    val liftPx = with(LocalDensity.current) { lift.toPx() }
    graphicsLayer {
        val v = p.value
        alpha = v.coerceIn(0f, 1f)
        val s = from + (1f - from) * v
        scaleX = s
        scaleY = s
        translationY = (1f - v) * liftPx
    }
}

/** Headline entrance: the text rises into view from below its own bottom edge (a "mask line"). */
fun Modifier.maskReveal(delayMs: Long = 0): Modifier = composed {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs)
        p.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 260f))
    }
    clipToBounds().graphicsLayer { translationY = (1f - p.value) * size.height * 1.15f }
}

/** Press feedback: shrink slightly while [interaction] is pressed, spring back on release. */
fun Modifier.pressScale(interaction: InteractionSource, pressed: Float = 0.96f): Modifier = composed {
    val isPressed by interaction.collectIsPressedAsState()
    val s by animateFloatAsState(
        if (isPressed) pressed else 1f,
        spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    graphicsLayer { scaleX = s; scaleY = s }
}
