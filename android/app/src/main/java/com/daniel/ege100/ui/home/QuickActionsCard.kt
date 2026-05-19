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

/**
 * Phase 4 Stage B3 + P4-D4 — блок «Быстрый старт» на главном.
 *
 * 3 быстрых действия: пробник math, пробник rus, варианты КИМ ФИПИ.
 * Тренажёрные кнопки удалены в P4-D4 (Convention #81) — тренажёры открываются
 * только через каталог: Решать → Предмет → Тип → 🎯 Тренажёры.
 */
@Composable
fun QuickActionsCard(
    onStartMockMath: () -> Unit,
    onStartMockRus: () -> Unit,
    onFipiVariants: () -> Unit,
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
