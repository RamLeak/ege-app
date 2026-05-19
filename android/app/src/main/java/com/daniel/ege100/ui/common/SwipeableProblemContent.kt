package com.daniel.ege100.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Phase 4 Stage P4-C3 part В2 (Convention #63) — горизонтальные свайпы
 * между задачами или словами в тренажёрах.
 *
 * Свайп **влево** → onNext(), **вправо** → onPrev(). Threshold =
 * screenWidth × 0.25 (примерно 100dp на 400dp экране). На границах
 * (нет prev / нет next) включается **резинка**: контент тянется только
 * на 1/3 от движения пальца и пружинит обратно при release.
 *
 * **Координация с SwipeBackContainer:** жест, начатый от левого края
 * (x < edgePx = 24dp), полностью игнорируется (передаётся выше для
 * swipe-back). Это исключает double-trigger когда пользователь свайпает
 * от края → swipe-back должен сработать, а не goPrev().
 *
 * **В тренажёрах**: `onSwipeStart` зовётся когда жест начался — в
 * AccentTrainer/WordBlankTrainer это отменяет `pendingAdvanceJob` чтобы
 * auto-advance не сработал во время свайпа.
 */
@Composable
fun SwipeableProblemContent(
    hasPrev: Boolean,
    hasNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    onSwipeStart: () -> Unit = {},
    edgeWidthDp: Int = 24,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val edgePx = with(density) { edgeWidthDp.dp.toPx() }
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(hasPrev, hasNext) {
                val screenWidth = size.width.toFloat()
                val threshold = screenWidth * 0.25f
                var skip = false
                detectHorizontalDragGestures(
                    onDragStart = { startOffset ->
                        // Жест из edge-зоны — отдаём SwipeBackContainer.
                        skip = startOffset.x < edgePx
                        if (!skip) onSwipeStart()
                    },
                    onDragEnd = {
                        if (skip) return@detectHorizontalDragGestures
                        val cur = offsetX.value
                        when {
                            cur < -threshold && hasNext -> {
                                scope.launch {
                                    onNext()
                                    offsetX.snapTo(0f)
                                }
                            }
                            cur > threshold && hasPrev -> {
                                scope.launch {
                                    onPrev()
                                    offsetX.snapTo(0f)
                                }
                            }
                            else -> {
                                scope.launch {
                                    offsetX.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = 0.85f,
                                            stiffness = Spring.StiffnessMediumLow,
                                        ),
                                    )
                                }
                            }
                        }
                        skip = false
                    },
                    onDragCancel = {
                        if (!skip) {
                            scope.launch {
                                offsetX.animateTo(0f, spring(0.85f, Spring.StiffnessMediumLow))
                            }
                        }
                        skip = false
                    },
                    onHorizontalDrag = { _, drag ->
                        if (skip) return@detectHorizontalDragGestures
                        val target = offsetX.value + drag
                        // Резинка /3 на границах: если двигаемся в сторону где
                        // нет prev/next — реальный сдвиг = 1/3 от пальца.
                        val effective = when {
                            target < 0f && !hasNext -> offsetX.value + drag / 3f
                            target > 0f && !hasPrev -> offsetX.value + drag / 3f
                            else -> target
                        }
                        scope.launch { offsetX.snapTo(effective) }
                    },
                )
            }
            .graphicsLayer { translationX = offsetX.value },
    ) {
        content()
    }
}
