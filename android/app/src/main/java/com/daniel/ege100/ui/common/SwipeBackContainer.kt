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
 * Phase 4 Stage P4-C3 part В1 (Convention #62) — iOS-style swipe-back.
 *
 * Свайп **от левого края (0..24dp)** вправо → возврат назад. От центра —
 * НЕ срабатывает (свайпы между задачами или скролл LazyColumn остаются).
 *
 * Визуальный feedback: контент следует за пальцем (animatable offsetX),
 * после отпускания пружинит обратно (spring 0.85). Если протянули
 * > 30% ширины экрана — onBack() и небольшая задержка перед pop.
 *
 * Заменяет глобальный `Modifier.edgeSwipeBack` на NavHost (Convention #20)
 * — там был только passive listener без visual. Сейчас визуальный отклик
 * + spring обратно делает жест «как в iOS».
 *
 * Применяется в КАЖДОМ detail-экране, НЕ в корневых табах:
 *   ProblemDetailScreen, ProblemListScreen, ErrorsListScreen, StatsScreen,
 *   MockExamCalendarScreen, MockExamDetailScreen, MockExamRunnerScreen,
 *   MockExamHistoryScreen, FipiVariantsScreen, AccentTrainerScreen,
 *   WordBlankTrainerScreen, ProfileScreen, SettingsScreen, QuickTrainerScreen,
 *   AccentCategoriesScreen, TypesScreen, SubtypesScreen, FavoritesScreen.
 */
@Composable
fun SwipeBackContainer(
    onBack: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val edgeWidthPx = with(density) { 24.dp.toPx() }
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (!enabled) Modifier else Modifier.pointerInput(Unit) {
                    val screenWidth = size.width.toFloat()
                    val triggerDistance = screenWidth * 0.30f
                    var startedFromEdge = false
                    var accumulatedDrag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { startOffset ->
                            startedFromEdge = startOffset.x <= edgeWidthPx
                            accumulatedDrag = 0f
                        },
                        onDragEnd = {
                            if (startedFromEdge && accumulatedDrag > triggerDistance) {
                                // Анимация ухода + onBack.
                                scope.launch {
                                    offsetX.animateTo(
                                        targetValue = screenWidth,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMediumLow,
                                        ),
                                    )
                                    onBack()
                                    offsetX.snapTo(0f)
                                }
                            } else {
                                // Пружинит обратно к 0.
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
                            startedFromEdge = false
                            accumulatedDrag = 0f
                        },
                        onDragCancel = {
                            if (startedFromEdge) {
                                scope.launch { offsetX.animateTo(0f, spring(0.85f, Spring.StiffnessMediumLow)) }
                            }
                            startedFromEdge = false
                            accumulatedDrag = 0f
                        },
                        onHorizontalDrag = { _, drag ->
                            if (startedFromEdge && drag > 0f) {
                                accumulatedDrag += drag
                                scope.launch { offsetX.snapTo(accumulatedDrag.coerceAtLeast(0f)) }
                            } else if (startedFromEdge && drag < 0f && offsetX.value > 0f) {
                                // Откат — позволяем тянуть обратно к нулю.
                                accumulatedDrag = (accumulatedDrag + drag).coerceAtLeast(0f)
                                scope.launch { offsetX.snapTo(accumulatedDrag) }
                            }
                        },
                    )
                },
            )
            .graphicsLayer { translationX = offsetX.value },
    ) {
        content()
    }
}
