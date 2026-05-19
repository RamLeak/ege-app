package com.daniel.ege100.ui.common

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daniel.ege100.ui.theme.BgElevated
import com.daniel.ege100.ui.theme.Label
import com.daniel.ege100.ui.theme.Separator
import com.daniel.ege100.ui.theme.SystemBlue
import com.daniel.ege100.ui.theme.SystemGreen
import com.daniel.ege100.ui.theme.SystemGreenTint
import com.daniel.ege100.ui.theme.SystemRed
import com.daniel.ege100.ui.theme.SystemRedTint

/**
 * Phase 4 Stage P4-C2 part Б (Convention #57) — кнопки букв для
 * тренажёров №9-12.
 *
 * Заменяют IosTextField + кнопку «Проверить» — одно нажатие сразу
 * проверяет ответ. На дисплее 2-3 кнопки в Row с равной шириной,
 * по 64dp высотой, scale 0.96 при тапе + haptic.
 *
 * Состояния:
 *  - `Idle` (selected = null) — нейтральные кнопки на BgElevated.
 *  - `Verdict` — выделяются три цвета: выбранная правильная → green,
 *    выбранная неправильная → red, правильная (НЕ выбранная) → green.
 *    Остальные приглушены.
 */
@Composable
fun LetterChoiceRow(
    choices: List<String>,
    selected: String?,
    correct: String?,
    showVerdict: Boolean,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        choices.forEach { letter ->
            LetterChoiceButton(
                letter = letter,
                isSelected = selected != null && letter == selected,
                isCorrect = showVerdict && correct != null && letter == correct,
                isWrong = showVerdict && selected != null && letter == selected && selected != correct,
                enabled = enabled && !showVerdict,
                onClick = { onSelect(letter) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun LetterChoiceButton(
    letter: String,
    isSelected: Boolean,
    isCorrect: Boolean,
    isWrong: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "letter-choice-scale",
    )

    val (bg, fg, borderColor) = when {
        isCorrect -> Triple(SystemGreenTint, SystemGreen, SystemGreen)
        isWrong -> Triple(SystemRedTint, SystemRed, SystemRed)
        isSelected -> Triple(SystemBlue, Color.White, SystemBlue)
        else -> Triple(BgElevated, Label, Separator)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(64.dp)
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(1.5.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
    ) {
        Text(
            text = letter,
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            color = fg,
        )
    }
}
