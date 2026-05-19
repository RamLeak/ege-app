package com.daniel.ege100.ui.trainer

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.daniel.ege100.ui.common.PrimaryButton
import com.daniel.ege100.ui.common.SecondaryButton
import com.daniel.ege100.ui.theme.BgElevated
import com.daniel.ege100.ui.theme.Label
import com.daniel.ege100.ui.theme.LabelSecondary
import com.daniel.ege100.ui.theme.SystemBlue
import com.daniel.ege100.ui.theme.SystemGreen
import com.daniel.ege100.ui.theme.SystemOrange
import com.daniel.ege100.ui.theme.SystemRed
import com.daniel.ege100.ui.theme.SystemYellow
import kotlin.random.Random

/**
 * Phase 4 Stage P4-D (Convention #75) — диалог поздравления после прохождения
 * тренажёра. Canvas-конфетти + 2 кнопки.
 *
 * Запускается из ViewModel'я тренажёра когда position+1 >= total. Параметр
 * `wordsCount` — сколько слов в тренажёре было; используется для текста
 * «Освоено: N слов».
 */
@Composable
fun CongratulationDialog(
    trainerName: String,
    wordsCount: Int,
    onClose: () -> Unit,
    onAgain: () -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ConfettiOverlay()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(BgElevated)
                        .padding(28.dp),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("🎉", fontSize = 64.sp)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Поздравляем!",
                            color = Label,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Ты прошёл тренажёр",
                            color = LabelSecondary,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            trainerName,
                            color = SystemBlue,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Освоено: $wordsCount " + wordsWord(wordsCount),
                            color = LabelSecondary,
                            fontSize = 14.sp,
                        )
                        Spacer(Modifier.height(24.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            SecondaryButton(
                                text = "Закрыть",
                                onClick = onClose,
                                modifier = Modifier.weight(1f),
                            )
                            PrimaryButton(
                                text = "Ещё раз",
                                onClick = onAgain,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun wordsWord(n: Int): String = when {
    n % 10 == 1 && n % 100 != 11 -> "слово"
    n % 10 in 2..4 && n % 100 !in 12..14 -> "слова"
    else -> "слов"
}

private data class Particle(
    val xFrac: Float,
    val delay: Float,
    val color: Color,
    val drift: Float,    // боковой снос
    val size: Float,
)

@Composable
private fun ConfettiOverlay() {
    val rng = remember { Random(System.currentTimeMillis()) }
    // SystemBlue/Green/... — это @Composable getters от LocalDarkOverride.
    // Их нельзя дёргать внутри remember-блока. Считываем здесь, в Composable scope.
    val colors = listOf(SystemBlue, SystemGreen, SystemOrange, SystemRed, SystemYellow)
    val particles = remember(colors) {
        List(50) {
            Particle(
                xFrac = rng.nextFloat(),
                delay = rng.nextFloat() * 0.6f,        // 0..0.6 от длительности
                color = colors[rng.nextInt(colors.size)],
                drift = (rng.nextFloat() - 0.5f) * 0.3f,
                size = 5f + rng.nextFloat() * 8f,
            )
        }
    }
    var t by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        animate(initialValue = 0f, targetValue = 1f, animationSpec = tween(3000)) { value, _ ->
            t = value
        }
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        particles.forEach { p ->
            val effT = (t - p.delay) / (1f - p.delay)
            if (effT in 0f..1f) {
                val x = (p.xFrac + p.drift * effT) * size.width
                val y = effT * size.height * 1.15f
                drawCircle(
                    color = p.color,
                    radius = p.size,
                    center = Offset(x, y),
                )
            }
        }
    }
}
