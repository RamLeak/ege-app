package com.daniel.ege100.ui.trainer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
 * Phase 4 Stage P4-D4 (Convention #79) — общая компонента для тренажёров с multi-choice
 * исправлением предложения. Используется в:
 *   - GrammarErrorTrainerScreen (русский №7): wrong_sentence + 4 варианта исправлений.
 *
 * При добавлении других задач «выбери правильный вариант» в P5+ (например,
 * паронимы с расширенной механикой, синтаксис) — переиспользовать.
 */
data class SentenceChoice(
    val text: String,
    val isCorrect: Boolean,
)

@Composable
fun SentenceChoiceTrainer(
    errorType: String,
    wrongSentence: String,
    options: List<SentenceChoice>,
    verdict: TapVerdict,
    selectedIndex: Int?,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (errorType.isNotBlank()) {
            Text(
                text = errorType,
                color = LabelSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.size(12.dp))
        }

        // Ошибочное предложение в красной карточке.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(SystemRedTint)
                .border(1.dp, SystemRed.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                .padding(16.dp),
        ) {
            Text(
                text = wrongSentence,
                color = Label,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        Spacer(Modifier.size(16.dp))

        Text(
            text = "Выбери правильное исправление:",
            color = LabelSecondary,
            fontSize = 13.sp,
        )
        Spacer(Modifier.size(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            options.forEachIndexed { index, option ->
                ChoiceCard(
                    letter = "ABCD".getOrNull(index)?.toString() ?: "·",
                    text = option.text,
                    isCorrect = option.isCorrect,
                    selected = selectedIndex == index,
                    verdict = verdict,
                    onClick = { onPick(index) },
                )
            }
        }
    }
}

@Composable
private fun ChoiceCard(
    letter: String,
    text: String,
    isCorrect: Boolean,
    selected: Boolean,
    verdict: TapVerdict,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val (bg, border) = when {
        verdict == TapVerdict.CORRECT && selected -> SystemGreenTint to SystemGreen
        verdict == TapVerdict.WRONG && selected -> SystemRedTint to SystemRed
        verdict == TapVerdict.WRONG && isCorrect -> SystemGreenTint to SystemGreen
        else -> BgElevated to Separator
    }
    val enabled = verdict == TapVerdict.NONE
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.5.dp, border, RoundedCornerShape(14.dp))
            .then(
                if (enabled) Modifier.clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                } else Modifier,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Separator.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(letter, color = LabelSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.size(12.dp))
        Text(
            text = text,
            color = Label,
            fontSize = 15.sp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
