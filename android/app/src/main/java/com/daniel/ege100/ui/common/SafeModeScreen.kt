package com.daniel.ege100.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Phase 4 Stage P4-D3 (Convention #69) — экран безопасного режима.
 *
 * Преднамеренно НЕ использует тему/Compose-state/DataStore приложения —
 * хардкод цветов и литералы. Цель: ноль внешних зависимостей кроме базового
 * Material3, чтобы этот экран открылся даже если в основном приложении
 * что-то фундаментально сломано (corpus.db, DataStore, settings flow).
 */
@Composable
fun SafeModeScreen(onReset: () -> Unit) {
    val bg = Color(0xFF000000)
    val label = Color(0xFFFFFFFF)
    val labelSecondary = Color(0xFF98989E)
    val systemBlue = Color(0xFF0A84FF)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "⚠️",
                fontSize = 56.sp,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Безопасный режим",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = label,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Приложение несколько раз подряд вылетало при запуске. " +
                    "Возможно, что-то сломалось в данных или последнем обновлении. " +
                    "Нажми «Сбросить», чтобы попробовать снова. " +
                    "Crash-логи сохранены — отправь их через Настройки → Поддержка после восстановления.",
                fontSize = 15.sp,
                color = labelSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onReset,
                colors = ButtonDefaults.buttonColors(
                    containerColor = systemBlue,
                    contentColor = label,
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(
                    text = "Сбросить и перезапустить",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
