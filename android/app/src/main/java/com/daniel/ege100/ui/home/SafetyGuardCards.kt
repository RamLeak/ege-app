package com.daniel.ege100.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daniel.ege100.ui.common.AppleCard
import com.daniel.ege100.ui.theme.BgElevated
import com.daniel.ege100.ui.theme.Label
import com.daniel.ege100.ui.theme.LabelSecondary
import com.daniel.ege100.ui.theme.SystemRed
import com.daniel.ege100.ui.theme.SystemRedTint

/**
 * Phase 3 Stage FINAL part В — карточка Safety Rule #5 (50 задач/неделю).
 *
 * Показывается под шапкой Главного экрана если за прошлую неделю было
 * решено меньше 50 задач. Это **визуальная индикация**, не блокировка —
 * пользователь видит, что подготовка замедляется.
 */
@Composable
fun WeeklyGuardCard(weekTotal: Int, threshold: Int = 50) {
    AppleCard(paddingDp = 18, background = SystemRedTint) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("⚠️", fontSize = 20.sp)
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "Прошлая неделя: $weekTotal из $threshold",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SystemRed,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Цель — минимум $threshold задач в неделю. На прошлой неделе ты решил $weekTotal — это меньше нормы. Подтяни на этой неделе.",
                fontSize = 13.sp,
                color = Label,
                lineHeight = 18.sp,
            )
        }
    }
}

/**
 * Phase 3 Stage FINAL part В — диалог Safety Rule #6 (контрольная точка 8 недель).
 *
 * Показывается один раз когда `eightWeekGuardActive == true`. После
 * confirm — `vm.dismissEightWeekGuard()` снимает флаг до следующей
 * 8-недельной отметки.
 */
@Composable
fun EightWeekCheckpointDialog(periodTotal: Int, threshold: Int = 300, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onConfirm,
        containerColor = BgElevated,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = "🎯 Контрольная точка",
                fontWeight = FontWeight.SemiBold,
                color = Label,
            )
        },
        text = {
            Text(
                text = "За последние 8 недель ты решил $periodTotal задач. Цель — минимум $threshold. " +
                    "Это сигнал что подготовка идёт медленно. Месяц активного использования без новых фич — фокус на решении задач.",
                fontSize = 14.sp,
                color = LabelSecondary,
                lineHeight = 20.sp,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Понял", color = SystemRed, fontWeight = FontWeight.SemiBold)
            }
        },
    )
}
