package com.daniel.ege100.ui.common

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.daniel.ege100.ui.theme.BgElevated2
import com.daniel.ege100.ui.theme.SystemBlue

/**
 * Phase 3 part А1 — iOS-style прогресс-бар, видимый в обеих темах.
 *
 * Tradeoffs со старым ProgressBar в AccentTrainer/WordBlankTrainer:
 *   - Высота 6dp вместо 4dp — заметнее.
 *   - Track: BgElevated2 (dynamic) вместо хардкода `0x33FFFFFF` — в светлой
 *     теме на белом фоне теперь видимый светло-серый, а не «бледный белый».
 *   - Spring NoBouncy + StiffnessMediumLow — плавнее анимация заполнения,
 *     не дёргается при быстром перелистывании.
 *   - Capsule shape (clip RoundedCornerShape(height/2)) — полное скругление.
 */
@Composable
fun AppleProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    fillColor: Color? = null,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "progress-fill",
    )
    val capsule = RoundedCornerShape(height / 2)
    // Phase 4 Stage P4-D2 part Б (Convention #65) — кастомный цвет для
    // прогресс-баров типов/подвидов (серый/оранжевый/синий/зелёный по %).
    // Default остаётся SystemBlue для обратной совместимости с journal+stats.
    val color = fillColor ?: SystemBlue
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(capsule)
            .background(BgElevated2),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animatedProgress)
                .fillMaxHeight()
                .clip(capsule)
                .background(color),
        )
    }
}
