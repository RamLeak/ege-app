package com.daniel.ege100.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daniel.ege100.data.PredictorResult
import com.daniel.ege100.ui.common.AppleCard
import com.daniel.ege100.ui.common.AppleProgressBar
import com.daniel.ege100.ui.theme.Label
import com.daniel.ege100.ui.theme.LabelSecondary
import com.daniel.ege100.ui.theme.LabelTertiary
import com.daniel.ege100.ui.theme.SystemGreen
import com.daniel.ege100.ui.theme.SystemOrange
import com.daniel.ege100.ui.theme.SystemRed

/**
 * Phase 3 Stage B part Г — карточка предиктора балла.
 *
 *   📊 Прогноз балла
 *   Математика  72/100   цель 80    [progress bar]
 *   Русский     85/100   цель 80    [progress bar]
 *
 * Цветовая логика для testScore vs targetScore:
 *   testScore >= target          → SystemGreen
 *   testScore >= target - 10     → SystemOrange
 *   иначе                        → SystemRed
 *
 * При confidence < 0.3 — подсказка «Реши больше задач для точного прогноза».
 */
@Composable
fun PredictorCard(
    math: PredictorResult,
    rus: PredictorResult,
    targetScore: Int,
) {
    AppleCard(paddingDp = 22) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📊", fontSize = 24.sp)
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "Прогноз балла",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Label,
                )
            }
            Spacer(Modifier.height(18.dp))
            PredictorRow(label = "Математика", result = math, target = targetScore)
            Spacer(Modifier.height(16.dp))
            PredictorRow(label = "Русский", result = rus, target = targetScore)
        }
    }
}

@Composable
private fun PredictorRow(label: String, result: PredictorResult, target: Int) {
    val color = scoreColor(result.testScore, target)
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = label,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Label,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${result.testScore}",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            Text(
                text = " /100",
                fontSize = 14.sp,
                color = LabelSecondary,
                modifier = Modifier.padding(start = 2.dp, bottom = 4.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Цель: $target",
            fontSize = 12.sp,
            color = LabelTertiary,
        )
        Spacer(Modifier.height(8.dp))
        AppleProgressBar(progress = result.testScore / 100f)
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (result.confidence < 0.3f) {
                "Реши больше задач для точного прогноза"
            } else {
                "Уверенность ${(result.confidence * 100).toInt()}%  ·  ${result.rawScore} / ${result.maxRaw} первичных"
            },
            fontSize = 12.sp,
            color = LabelTertiary,
        )
    }
}

@Composable
@ReadOnlyComposable
private fun scoreColor(score: Int, target: Int): Color = when {
    score >= target -> SystemGreen
    score >= target - 10 -> SystemOrange
    else -> SystemRed
}
