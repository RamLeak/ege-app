package com.daniel.ege100.ui.nav

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daniel.ege100.ui.theme.BgElevated
import com.daniel.ege100.ui.theme.Label
import com.daniel.ege100.ui.theme.LabelSecondary
import com.daniel.ege100.ui.theme.LabelTertiary
import com.daniel.ege100.ui.theme.LocalDarkOverride
import com.daniel.ege100.ui.theme.SystemBlue

/**
 * Phase 4 Stage P4-D6 (Convention #89) — нижняя навигация в стиле iOS Liquid Glass.
 *
 * **Настоящий backdrop blur** через `android.graphics.RenderEffect.createBlurEffect`
 * на Android 12+ (Build.VERSION_CODES.S). Для более старых (Android 8-11) — fallback
 * на полупрозрачный фон без размытия.
 *
 * Размещается как `bottomBar` в Scaffold. Внутри сама обрабатывает
 * `windowInsetsPadding(WindowInsets.navigationBars)` — чтобы капсула не залазила
 * под системную navigation bar (3-кнопочная или жестовая полоска).
 *
 * Layout:
 *   - Box с боковыми отступами 12dp + 8dp вертикально.
 *   - Закруглённая капсула 28dp радиус, высота 64dp.
 *   - Иконки (текстовые emoji) 26sp, активная масштабируется до 1.18 spring.
 *   - Текст под иконкой 11sp Medium, при активном — SystemBlue + SemiBold.
 *
 * @param items список табов (label + emoji + route).
 * @param currentRoute текущий route для определения selected.
 * @param onTabClick callback с выбранным route.
 */
@Composable
fun LiquidGlassBottomNav(
    items: List<LiquidGlassTab>,
    selectedKey: String?,
    onTabClick: (LiquidGlassTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalDarkOverride.current ?: androidx.compose.foundation.isSystemInDarkTheme()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        // Phase 4 Stage P4-D7 (Convention #92) — двухслойная структура:
        // (1) backdrop с blur+тинт, (2) иконки поверх через matchParentSize.
        // Это критично: blur не должен применяться к иконкам — они становятся
        // нечитаемыми. Резкие иконки на размытом фоне = настоящий iOS-glass.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(28.dp)),
        ) {
            // Слой 1 — размытый фон ПОЗАДИ.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .liquidGlassBackground(isDark = isDark),
            )
            // Слой 2 — иконки ПОВЕРХ, РЕЗКИЕ (без blur'а).
            Row(
                modifier = Modifier.matchParentSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { tab ->
                    LiquidGlassTabItem(
                        tab = tab,
                        isSelected = tab.key == selectedKey,
                        onClick = { onTabClick(tab) },
                    )
                }
            }
        }
    }
}

/**
 * Один tab в Liquid Glass nav.
 *
 *   - Иконка scale 1.0 → 1.18 при выборе (spring MediumBouncy).
 *   - Цвет иконки + label LabelSecondary → SystemBlue (tween 200ms).
 *   - Текст label при выборе — SemiBold.
 */
@Composable
private fun LiquidGlassTabItem(
    tab: LiquidGlassTab,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }

    val iconScale by animateFloatAsState(
        targetValue = if (isSelected) 1.18f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "tab_icon_scale",
    )
    val tintColor by animateColorAsState(
        targetValue = if (isSelected) SystemBlue else LabelSecondary,
        animationSpec = tween(durationMillis = 220),
        label = "tab_tint",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .scale(iconScale),
            contentAlignment = Alignment.Center,
        ) {
            // Текстовый эмодзи. tint через Color применить нельзя — emoji
            // рисуется системой, но всё равно выглядит уместно (синева
            // активного таба видна через label под ним).
            Text(
                text = tab.icon,
                fontSize = 22.sp,
            )
        }
        Text(
            text = tab.label,
            fontSize = 11.sp,
            color = tintColor,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/**
 * Liquid Glass фон: полупрозрачный цвет + RenderEffect blur 20f на Android 12+.
 *
 *  - **Android 12+**: `RenderEffect.createBlurEffect(20f, 20f, CLAMP)` — настоящий
 *    backdrop blur. Layer кладёт полупрозрачный цвет, поверх blur размывает то
 *    что находится позади (контент Scaffold'а). На пустом фоне Bg это даёт
 *    эффект «лёгкой дымки» на цвете темы.
 *  - **Android 8-11**: fallback на полупрозрачный фон без размытия.
 *  - Граничный border 0.5dp с вертикальным градиентом для «iOS-аутлайна».
 */
@Composable
private fun Modifier.liquidGlassBackground(isDark: Boolean): Modifier {
    // Phase 4 Stage P4-D7 (Convention #92) — blur снижен с 20f до 8f,
    // opacity поднят с 0.75/0.78 до 0.92. Это даёт «деликатное стеклянное»
    // ощущение без разрушения читаемости. Иконки теперь рисуются ПОВЕРХ
    // этого фонового слоя через matchParentSize Box — они НЕ размываются.
    //
    // Phase 5 perf fix P4 (tag `phase-5-fix-3-glass-opacity`) — opacity
    // снижен с 0.92 до 0.78. Раньше при 0.92 backdrop был почти
    // непрозрачным и blur 8px физически не был виден — GPU тратил
    // 2-5 ms/кадр на эффект, который пользователь не замечал. При 0.78
    // позади подложки угадывается scroll-контент (текст / прогресс-бары /
    // карточки) — RenderEffect blur визуально оправдан, и кадровый
    // бюджет освобождается для AnimatedContent transitions. Convention
    // #92 (двухслойная архитектура) сохранена: иконки на отдельном
    // слое поверх backdrop без blur'а — читаемость не страдает.
    val backgroundColor = if (isDark) {
        Color(0xFF1C1C1E).copy(alpha = 0.78f)
    } else {
        Color(0xFFFFFFFF).copy(alpha = 0.78f)
    }
    val borderTop = if (isDark) {
        Color.White.copy(alpha = 0.10f)
    } else {
        Color.White.copy(alpha = 0.55f)
    }
    val borderBottom = if (isDark) {
        Color.White.copy(alpha = 0.04f)
    } else {
        Color.Black.copy(alpha = 0.06f)
    }

    return this
        .then(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Modifier.graphicsLayer {
                    renderEffect = RenderEffect.createBlurEffect(
                        8f, 8f,
                        Shader.TileMode.CLAMP,
                    ).asComposeRenderEffect()
                }
            } else {
                Modifier
            },
        )
        .background(backgroundColor)
        .border(
            width = 0.5.dp,
            brush = Brush.verticalGradient(
                colors = listOf(borderTop, borderBottom),
            ),
            shape = RoundedCornerShape(28.dp),
        )
}

/**
 * Описание одного таба. `key` — стабильная строка для определения
 * `selectedKey` (например имя route-класса или slug).
 */
data class LiquidGlassTab(
    val key: String,
    val label: String,
    val icon: String,
    val route: Any,
)
