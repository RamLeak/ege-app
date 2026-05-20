package com.daniel.ege100.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daniel.ege100.ui.common.DangerButton
import com.daniel.ege100.ui.common.PrimaryButton
import com.daniel.ege100.ui.common.SecondaryButton
import com.daniel.ege100.ui.common.cardsWord
import com.daniel.ege100.ui.common.daysWord
import com.daniel.ege100.ui.theme.Bg
import com.daniel.ege100.ui.theme.BgElevated
import com.daniel.ege100.ui.theme.Label
import com.daniel.ege100.ui.theme.LabelSecondary
import com.daniel.ege100.ui.theme.LabelTertiary
import com.daniel.ege100.ui.theme.SystemBlue
import com.daniel.ege100.ui.theme.SystemBlueTint
import com.daniel.ege100.ui.theme.SystemRed

/**
 * Phase 5 Stage E4 — настройка лимита SRS-карточек на день.
 *
 * Простой stepper по шагу 10, диапазон 10..200 (Master prompt §1.5).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SrsDailyLimitBottomSheet(
    current: Int,
    onSave: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(current.coerceIn(10, 200)) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Bg,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = "📚 Карточек в день",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Label,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Сколько максимум SRS-карточек показывать за одну сессию повторения.",
                fontSize = 14.sp,
                color = LabelSecondary,
            )
            Spacer(Modifier.height(28.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StepperButton("−10", enabled = value > 10) {
                    value = (value - 10).coerceAtLeast(10)
                }
                Spacer(Modifier.size(10.dp))
                StepperButton("−1", enabled = value > 10) {
                    value = (value - 1).coerceAtLeast(10)
                }
                Spacer(Modifier.size(16.dp))
                Text(
                    text = value.toString(),
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    color = SystemBlue,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                Spacer(Modifier.size(16.dp))
                StepperButton("+1", enabled = value < 200) {
                    value = (value + 1).coerceAtMost(200)
                }
                Spacer(Modifier.size(10.dp))
                StepperButton("+10", enabled = value < 200) {
                    value = (value + 10).coerceAtMost(200)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "$value ${cardsWord(value)}",
                fontSize = 14.sp,
                color = LabelTertiary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))

            // Presets row.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(10, 25, 50, 100, 200).forEach { preset ->
                    PresetChip(
                        value = preset,
                        selected = value == preset,
                        onClick = { value = preset },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                text = "Сохранить",
                onClick = { onSave(value) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            SecondaryButton(
                text = "Отмена",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun StepperButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (enabled) SystemBlueTint else BgElevated)
            .clickable(enabled = enabled) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) SystemBlue else LabelTertiary,
        )
    }
}

@Composable
private fun PresetChip(
    value: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .background(if (selected) SystemBlue else BgElevated)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = value.toString(),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Label else LabelSecondary,
        )
    }
}

/**
 * Phase 5 Stage E4 — подтверждение сброса SRS-streak.
 *
 * Reset обнуляет current + max. Это destructive операция, поэтому требуется
 * явное подтверждение через DangerButton (red CTA).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SrsStreakResetBottomSheet(
    currentStreak: Int,
    maxStreak: Int,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Bg,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = "🔥 SRS-streak",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Label,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Текущая серия: $currentStreak ${daysWord(currentStreak)}\n" +
                    "Максимум: $maxStreak ${daysWord(maxStreak)}",
                fontSize = 15.sp,
                color = LabelSecondary,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Сброс обнулит и текущий, и максимальный streak. Это нельзя отменить.",
                fontSize = 13.sp,
                color = SystemRed,
            )
            Spacer(Modifier.height(24.dp))
            DangerButton(
                text = "Сбросить streak",
                onClick = onReset,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            SecondaryButton(
                text = "Отмена",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
