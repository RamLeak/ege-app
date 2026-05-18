package com.daniel.ege100.ui.common

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daniel.ege100.ui.theme.BgElevated
import com.daniel.ege100.ui.theme.Label
import com.daniel.ege100.ui.theme.LabelSecondary
import com.daniel.ege100.ui.theme.LabelTertiary

/**
 * Apple-style карточки (STAGE_3_POLISH §Б4, §Б5).
 *
 *   AppleCard          — крупная карточка с corners 20dp, фон BgElevated,
 *                        padding 20dp. Тап → scale 0.98 + haptic.
 *   AppleListRow       — строка списка с leading-иконкой в круге, title,
 *                        subtitle, trailing ›. Pressable.
 *   IconCircle         — иконка в круге фиксированного размера 44/32dp.
 */

@Composable
fun AppleCard(
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    background: Color = BgElevated,
    cornerRadiusDp: Int = 24,
    paddingDp: Int = 22,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadiusDp.dp)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && onClick != null) 0.985f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "card-press",
    )
    val haptic = LocalHapticFeedback.current

    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interaction,
            indication = null,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        )
    } else Modifier

    Column(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(shape)
            .background(background)
            .then(clickModifier)
            .padding(paddingDp.dp),
    ) {
        content()
    }
}

@Composable
fun AppleListRow(
    title: String,
    subtitle: String? = null,
    leadingEmoji: String? = null,
    leadingTint: Color? = null,
    trailing: String? = "›",
    onClick: () -> Unit,
) {
    AppleCard(onClick = onClick, paddingDp = 18, cornerRadiusDp = 22) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (leadingEmoji != null) {
                IconCircle(emoji = leadingEmoji, tint = leadingTint)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Label,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        fontSize = 13.sp,
                        color = LabelSecondary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (trailing != null) {
                Text(
                    text = trailing,
                    fontSize = 22.sp,
                    color = LabelTertiary,
                )
            }
        }
    }
}

@Composable
fun IconCircle(
    emoji: String,
    sizeDp: Int = 40,
    tint: Color? = null,
) {
    val bg = tint ?: Color(0x1F0A84FF) // SystemBlue .12 by default
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(bg),
    ) {
        Text(text = emoji, fontSize = (sizeDp * 0.55f).sp)
    }
}
