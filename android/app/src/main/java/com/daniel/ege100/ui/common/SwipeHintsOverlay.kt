package com.daniel.ege100.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daniel.ege100.ui.theme.BgElevated
import com.daniel.ege100.ui.theme.Label
import com.daniel.ege100.ui.theme.LabelSecondary
import com.daniel.ege100.ui.theme.SystemBlue

/**
 * Phase 4 Stage P4-C3 part В2 (Convention #63) — onboarding-tooltip
 * про новые свайп-жесты.
 *
 * Показывается при первом открытии ProblemDetail / тренажёра, после тапа
 * по «Понятно» — `AppSettings.swipeHintsShown = true` навсегда.
 *
 * Покрывает 3 жеста:
 *  - Свайп от левого края → возврат назад.
 *  - Свайп влево → следующая задача.
 *  - Свайп вправо → предыдущая задача.
 */
@Composable
fun SwipeHintsOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.92f),
        exit = fadeOut() + scaleOut(targetScale = 0.92f),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(
                    interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource(),
                    indication = null,
                    onClick = onDismiss,
                ),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(BgElevated)
                    .padding(28.dp)
                    .fillMaxWidth(),
            ) {
                Text(
                    text = "👋 Жесты приложения",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Label,
                )
                Spacer(Modifier.height(16.dp))
                HintRow(
                    emoji = "👈",
                    text = "Свайп влево — следующая задача",
                )
                Spacer(Modifier.height(10.dp))
                HintRow(
                    emoji = "👉",
                    text = "Свайп вправо — предыдущая задача",
                )
                Spacer(Modifier.height(10.dp))
                HintRow(
                    emoji = "↩️",
                    text = "Свайп от левого края — назад",
                )
                Spacer(Modifier.height(20.dp))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(SystemBlue)
                        .clickable { onDismiss() },
                ) {
                    Text(
                        text = "Понятно",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun HintRow(emoji: String, text: String) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(emoji, fontSize = 24.sp)
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            fontSize = 15.sp,
            color = LabelSecondary,
            lineHeight = 20.sp,
            textAlign = TextAlign.Start,
        )
    }
}
