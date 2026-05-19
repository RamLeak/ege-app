package com.daniel.ege100.ui.trainer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daniel.ege100.ui.theme.BgElevated
import com.daniel.ege100.ui.theme.Label
import com.daniel.ege100.ui.theme.LabelSecondary
import com.daniel.ege100.ui.theme.Separator
import com.daniel.ege100.ui.theme.SystemGreen
import com.daniel.ege100.ui.theme.SystemGreenTint
import com.daniel.ege100.ui.theme.SystemRed
import com.daniel.ege100.ui.theme.SystemRedTint

/**
 * Phase 4 Stage P4-D (Convention #71) — общая компонента: предложение разбито
 * на слова-кнопки, пользователь тапает «проблемное» слово.
 *
 * Используется в:
 *   - PleonasmTrainer (№6): tap на лишнее слово.
 *   - GrammarErrorTrainer (№7): tap на слово с ошибкой.
 *
 * `targetWord` — лемма (normalized: lowercase, ё→е, обрезаны знаки препинания).
 * При сравнении токены тоже нормализуются.
 *
 * Verdict:
 *   - Wrong: красная подсветка тапнутого слова + зелёная подсветка правильного.
 *   - Correct: зелёная подсветка тапнутого (которое и есть target).
 */
enum class TapVerdict { NONE, CORRECT, WRONG }

private val PUNCT = ".,;!?:«»\"()[]…—"

private fun normalize(s: String): String =
    s.trim(*PUNCT.toCharArray()).lowercase().replace('ё', 'е').trim()

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordTapInSentenceTrainer(
    instruction: String,
    sentence: String,
    targetWord: String,
    verdict: TapVerdict,
    tappedWord: String?,
    onTap: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val target = normalize(targetWord)

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = instruction,
            color = LabelSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(16.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            sentence.split(' ').filter { it.isNotBlank() }.forEach { rawWord ->
                val norm = normalize(rawWord)
                val isTarget = norm == target
                val isTapped = tappedWord != null && normalize(tappedWord) == norm

                val (bg, border, fg) = when {
                    verdict == TapVerdict.CORRECT && isTapped -> Triple(SystemGreenTint, SystemGreen, Label)
                    verdict == TapVerdict.WRONG && isTapped -> Triple(SystemRedTint, SystemRed, Label)
                    verdict == TapVerdict.WRONG && isTarget -> Triple(SystemGreenTint, SystemGreen, Label)
                    else -> Triple(BgElevated, Separator, Label)
                }

                WordChip(
                    word = rawWord,
                    bg = bg,
                    border = border,
                    fg = fg,
                    enabled = verdict == TapVerdict.NONE,
                    onClick = { onTap(rawWord) },
                )
            }
        }
    }
}

@Composable
private fun WordChip(
    word: String,
    bg: androidx.compose.ui.graphics.Color,
    border: androidx.compose.ui.graphics.Color,
    fg: androidx.compose.ui.graphics.Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .then(
                if (enabled) Modifier.clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                } else Modifier
            )
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Text(
            text = word,
            color = fg,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
