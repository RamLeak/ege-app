package com.daniel.ege100.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daniel.ege100.ui.common.AppleListRow
import com.daniel.ege100.ui.theme.Label
import com.daniel.ege100.ui.theme.SystemBlueTint
import com.daniel.ege100.ui.theme.SystemGreenTint
import com.daniel.ege100.ui.theme.SystemOrange

/**
 * Phase 4 Stage B3 + P4-D — блок «Быстрый старт» на главном.
 *
 * Действия: пробник math, пробник rus, варианты КИМ ФИПИ, случайный тренажёр,
 * все тренажёры. Кнопка «Решить слабые места» уже есть в RadarCard — не дублируем.
 */
@Composable
fun QuickActionsCard(
    onStartMockMath: () -> Unit,
    onStartMockRus: () -> Unit,
    onFipiVariants: () -> Unit,
    onRandomTrainer: () -> Unit = {},
    onAllTrainers: () -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "🚀 Быстрый старт",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = Label,
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AppleListRow(
                title = "🎲 Случайный тренажёр",
                subtitle = "Один из 20 — выбирается наугад",
                leadingEmoji = "🎲",
                leadingTint = SystemOrange.copy(alpha = 0.18f),
                onClick = onRandomTrainer,
            )
            AppleListRow(
                title = "Все тренажёры",
                subtitle = "20 тренажёров: ударения, орфография, паронимы, математика",
                leadingEmoji = "📚",
                leadingTint = SystemGreenTint,
                onClick = onAllTrainers,
            )
            AppleListRow(
                title = "Пробник: Математика",
                subtitle = "19 заданий, по одному из каждого типа",
                leadingEmoji = "📐",
                leadingTint = SystemBlueTint,
                onClick = onStartMockMath,
            )
            AppleListRow(
                title = "Пробник: Русский",
                subtitle = "26 заданий из типов №1-26",
                leadingEmoji = "✍️",
                leadingTint = SystemBlueTint,
                onClick = onStartMockRus,
            )
            AppleListRow(
                title = "Варианты КИМ ФИПИ",
                subtitle = "Официальные пробники прошлых лет",
                leadingEmoji = "📂",
                leadingTint = SystemBlueTint,
                onClick = onFipiVariants,
            )
        }
    }
}

