package com.daniel.ege100.ui.common

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Phase 4 Stage P4-C3 part Б (Convention #61) — iOS-style плавный fling
 * для скролла. Стандартный compose-fling резко останавливается, после
 * iOS он ощущается «прыгучим». Здесь мы используем exponentialDecay с
 * frictionMultiplier 0.7 (по умолчанию 1.0 — быстрее затухает).
 *
 * 0.7 даёт ощутимо более длинный hover после быстрого свайпа, при этом
 * не превращаясь в «бесконечную инерцию». Тестировалось на Samsung.
 *
 * Использование: `SmoothLazyColumn { items(...) { ... } }` — drop-in
 * замена для `LazyColumn`. flingBehavior уже встроен.
 */
@Composable
fun rememberSmoothFlingBehavior(frictionMultiplier: Float = 0.7f): FlingBehavior {
    val decay = exponentialDecay<Float>(frictionMultiplier = frictionMultiplier)
    return remember(decay) { SmoothFlingBehavior(decay) }
}

private class SmoothFlingBehavior(
    private val flingDecay: DecayAnimationSpec<Float>,
) : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        if (abs(initialVelocity) <= 1f) return initialVelocity
        var velocityLeft = initialVelocity
        var lastValue = 0f
        AnimationState(
            initialValue = 0f,
            initialVelocity = initialVelocity,
        ).animateDecay(flingDecay) {
            val delta = value - lastValue
            val consumed = scrollBy(delta)
            lastValue = value
            velocityLeft = this.velocity
            // Если скролл упёрся (delta vs consumed расходится) — обрываем анимацию.
            if (abs(delta - consumed) > 0.5f) this.cancelAnimation()
        }
        return velocityLeft
    }
}

/**
 * Drop-in замена для `LazyColumn` с iOS-style fling. Все параметры
 * проксируются в стандартный `LazyColumn`. При необходимости передать
 * нестандартный `flingBehavior` — используй `LazyColumn` напрямую.
 */
@Composable
fun SmoothLazyColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical =
        if (!reverseLayout) Arrangement.Top else Arrangement.Bottom,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    userScrollEnabled: Boolean = true,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = modifier,
        state = state,
        contentPadding = contentPadding,
        reverseLayout = reverseLayout,
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        flingBehavior = rememberSmoothFlingBehavior(),
        userScrollEnabled = userScrollEnabled,
        content = content,
    )
}

