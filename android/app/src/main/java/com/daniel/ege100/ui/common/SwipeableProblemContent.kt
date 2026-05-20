package com.daniel.ege100.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.channels.Channel
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
 *
 * Phase 5 perf fix P1+P2 (tag `phase-5-fix-1-swipe-perf`):
 *  1. `Channel<Float>(CONFLATED)` + один LaunchedEffect для drag-tracking
 *     вместо `scope.launch { offsetX.snapTo(...) }` на каждое drag event.
 *     Старая версия запускала 60+ coroutine/сек, новая — одна coroutine
 *     на весь life-cycle, drag events идут через `trySend` (non-suspend,
 *     дешёвый). Если drag event приходит быстрее чем previous обработан,
 *     CONFLATED-канал перезаписывает — для translation важно только
 *     последнее значение.
 *  2. `pointerInput(Unit)` вместо `pointerInput(hasPrev, hasNext)` —
 *     раньше при смене availability prev/next Compose **пересоздавал
 *     gesture detector целиком**, отменяя текущие drag-coroutine. На
 *     крайних задачах (когда hasPrev/hasNext меняется на границах)
 *     свайпы дёргались. Теперь detector создаётся один раз; свежие
 *     значения hasPrev/hasNext/onPrev/onNext/onSwipeStart забираются
 *     через `rememberUpdatedState` (без пересоздания detector'а).
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

    // Phase 5 perf fix P2 — свежие значения замыкаемых callback'ов и
    // флагов hasPrev/hasNext без пересоздания pointerInput-блока.
    val currentHasPrev by rememberUpdatedState(hasPrev)
    val currentHasNext by rememberUpdatedState(hasNext)
    val currentOnPrev by rememberUpdatedState(onPrev)
    val currentOnNext by rememberUpdatedState(onNext)
    val currentOnSwipeStart by rememberUpdatedState(onSwipeStart)

    // Phase 5 perf fix P1 — single-producer/single-consumer канал для
    // drag delta. CONFLATED дропает старые непрочитанные значения —
    // нам важно только последнее (текущее положение пальца).
    val dragChannel = remember { Channel<Float>(capacity = Channel.CONFLATED) }
    LaunchedEffect(Unit) {
        for (target in dragChannel) {
            offsetX.snapTo(target)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // Phase 4 Stage P4-D2 part Г (Convention #67) — defensive
                // try/catch вокруг detectHorizontalDragGestures.
                val screenWidth = size.width.toFloat()
                val threshold = screenWidth * 0.25f
                var skip = false
                try {
                    detectHorizontalDragGestures(
                        onDragStart = { startOffset ->
                            skip = startOffset.x < edgePx
                            if (!skip) currentOnSwipeStart()
                        },
                        onDragEnd = {
                            if (skip) {
                                skip = false
                                return@detectHorizontalDragGestures
                            }
                            val cur = offsetX.value
                            when {
                                cur < -threshold && currentHasNext -> {
                                    scope.launch {
                                        currentOnNext()
                                        offsetX.snapTo(0f)
                                    }
                                }
                                cur > threshold && currentHasPrev -> {
                                    scope.launch {
                                        currentOnPrev()
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
                                target < 0f && !currentHasNext -> offsetX.value + drag / 3f
                                target > 0f && !currentHasPrev -> offsetX.value + drag / 3f
                                else -> target
                            }
                            // Phase 5 perf fix P1 — trySend дешёвый, не suspend,
                            // не создаёт новую coroutine на каждый кадр.
                            dragChannel.trySend(effective)
                        },
                    )
                } catch (cancel: kotlinx.coroutines.CancellationException) {
                    throw cancel  // CancellationException нужно пробросить дальше.
                } catch (e: Throwable) {
                    android.util.Log.e("SwipeableProblem", "gesture handler failed", e)
                    com.daniel.ege100.data.BreadcrumbLog.add(
                        "SwipeGesture failed: ${e.javaClass.simpleName} ${e.message?.take(80)}",
                    )
                    dragChannel.trySend(0f)
                }
            }
            .graphicsLayer { translationX = offsetX.value },
    ) {
        content()
    }
}
