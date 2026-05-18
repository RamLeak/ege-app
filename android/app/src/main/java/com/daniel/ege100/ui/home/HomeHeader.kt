package com.daniel.ege100.ui.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daniel.ege100.ui.common.daysWord
import com.daniel.ege100.ui.theme.Label
import com.daniel.ege100.ui.theme.LabelSecondary
import com.daniel.ege100.ui.theme.LabelTertiary
import com.daniel.ege100.ui.theme.SystemBlue
import com.daniel.ege100.ui.theme.SystemBlueTint
import com.daniel.ege100.ui.theme.SystemOrange

/**
 * Phase 3 Stage B part Б — шапка Главного экрана.
 *
 *   ┌───────────────────────────────[D]┐
 *   │ Привет, Daniel!                  │
 *   │ 🔥 7 дней  ·  📅 213 дней до ЕГЭ │
 *   └──────────────────────────────────┘
 *
 * При streak >= 7 значение 🔥 раскрашивается SystemOrange. Цифра streak
 * имеет лёгкий bounce при росте через animateFloatAsState.
 */
@Composable
fun HomeHeader(
    name: String,
    streak: Int,
    daysUntilExam: Int,
    onAvatarClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = if (name.isNotBlank()) "Привет, $name!" else "Привет!",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Label,
                letterSpacing = (-0.5).sp,
                lineHeight = 38.sp,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.size(12.dp))
            AvatarChip(
                initial = name.trim().firstOrNull()?.uppercaseChar()?.toString(),
                onClick = onAvatarClick,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            MetricChip(
                emoji = "🔥",
                value = streak.toString(),
                label = daysWord(streak),
                accent = if (streak >= 7) SystemOrange else LabelSecondary,
                animateGrow = streak,
            )
            Spacer(Modifier.size(12.dp))
            Text("·", color = LabelTertiary, fontSize = 16.sp)
            Spacer(Modifier.size(12.dp))
            MetricChip(
                emoji = "📅",
                value = daysUntilExam.toString(),
                label = "${daysWord(daysUntilExam)} до ЕГЭ",
                accent = LabelSecondary,
            )
        }
    }
}

@Composable
private fun AvatarChip(initial: String?, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(SystemBlueTint)
            .clickable { onClick() },
    ) {
        if (initial != null) {
            Text(
                text = initial,
                color = SystemBlue,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
        } else {
            Text("👤", fontSize = 22.sp)
        }
    }
}

@Composable
private fun MetricChip(
    emoji: String,
    value: String,
    label: String,
    accent: androidx.compose.ui.graphics.Color,
    animateGrow: Int = 0,
) {
    // Bounce при росте streak (animateGrow меняется → ключ key для scale).
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "metric-bounce-$animateGrow",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.scale(scale),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(emoji, fontSize = 18.sp)
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = accent,
        )
        Text(
            text = label,
            fontSize = 14.sp,
            color = LabelSecondary,
        )
    }
}
