package com.daniel.ege100.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daniel.ege100.ui.common.AppleCard
import com.daniel.ege100.ui.theme.Label
import com.daniel.ege100.ui.theme.LabelSecondary

/**
 * Phase 3 Stage FINAL part Е — welcome-карточка для новых пользователей
 * (`totalAttempts == 0`).
 *
 * Объясняет что появится в Главном экране после первых решённых задач —
 * прогноз балла, радар, цитата дня. Без CTA-кнопки: пользователь сам
 * найдёт каталог через таб «Решать» (это часть знакомства с навигацией).
 */
@Composable
fun WelcomeCard(modifier: Modifier = Modifier) {
    AppleCard(modifier = modifier, paddingDp = 22) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("🎯", fontSize = 44.sp)
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Начни подготовку",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = Label,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Реши первые задачи через таб «Решать» — здесь появятся прогноз балла, радар слабых мест и быстрый тренажёр.",
                fontSize = 14.sp,
                color = LabelSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
            )
        }
    }
}
