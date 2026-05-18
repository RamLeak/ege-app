package com.daniel.ege100.ui.modifiers

import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Stage 5 part Б — Edge swipe back (как в iOS/Telegram).
 *
 * Слушает pointer events на корневом уровне (NavHost). Реагирует только если
 * первый down произошёл в edge-зоне (x < edgeWidthDp от левого края). Если
 * за время жеста суммарное движение по X превысило triggerDistanceDp —
 * вызывает onSwipeBack().
 *
 * Координация с другими жестами:
 *   - Тренажёрные свайпы стартуют с центра экрана → не пересекаются.
 *   - Свайпы между задачами в ProblemDetailScreen игнорируют x<24dp →
 *     не пересекаются.
 *   - Native back gesture Android на нашем edge swipe не конфликтует, потому
 *     что Android system back-gesture работает отдельно, а наш swipe работает
 *     внутри Compose без consume.
 *
 * Реализация: НЕ consume'им события — pointerInput с PointerEventPass.Main
 * только наблюдает; LazyColumn внутри NavHost продолжает работать нормально.
 */
fun Modifier.edgeSwipeBack(
    enabled: Boolean = true,
    edgeWidthDp: Dp = 24.dp,
    triggerDistanceDp: Dp = 100.dp,
    onSwipeBack: () -> Unit,
): Modifier = composed {
    if (!enabled) return@composed this
    val density = LocalDensity.current
    val edgeWidthPx = remember(edgeWidthDp) { with(density) { edgeWidthDp.toPx() } }
    val triggerPx = remember(triggerDistanceDp) { with(density) { triggerDistanceDp.toPx() } }

    this.pointerInput(enabled) {
        awaitPointerEventScope {
            while (true) {
                // Initial pass — мы первые получаем сырое событие, до того
                // как ребёнок (LazyColumn и пр.) увидит. Не consume'им.
                val down = awaitPointerEvent(PointerEventPass.Initial)
                val first = down.changes.firstOrNull() ?: continue
                val startX = first.position.x
                if (startX >= edgeWidthPx) continue

                var totalDragX = 0f
                var triggered = false
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull() ?: break
                    totalDragX += change.positionChange().x
                    if (!triggered && totalDragX > triggerPx) {
                        triggered = true
                        onSwipeBack()
                    }
                    if (change.changedToUp() || event.changes.all { !it.pressed }) break
                }
            }
        }
    }
}
