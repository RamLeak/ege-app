package com.daniel.ege100.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daniel.ege100.ui.theme.Label
import com.daniel.ege100.ui.theme.LabelSecondary
import com.daniel.ege100.ui.theme.LabelTertiary
import com.daniel.ege100.ui.theme.SystemBlue

/**
 * Apple-style верхний бар (STAGE_3_POLISH §Б8): большой заголовок iOS-style
 * с back-стрелкой 32dp в круге и опциональным «right slot» (звезда/⋯).
 *
 * Две формы:
 *   - LargeTitleBar(title, subtitle?) — крупный header (34sp Bold) с
 *     опциональным subtitle (15sp LabelSecondary), padding 20×12dp.
 *   - SmallTitleBar(title) — компактная шапка для каталога (22sp SemiBold).
 */

@Composable
fun LargeTitleBar(
    title: String,
    subtitle: String? = null,
    trailingPosition: String? = null,
    onBack: (() -> Unit)? = null,
    rightContent: (@Composable () -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        // Top action row: back + optional right slot.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
        ) {
            if (onBack != null) {
                BackButton(onBack = onBack, haptic = haptic)
            } else {
                Spacer(Modifier.width(8.dp))
            }
            if (rightContent != null) rightContent()
        }
        Spacer(Modifier.height(6.dp))
        // Title row: large title left + optional position right.
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = title,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = Label,
                letterSpacing = (-0.5).sp,
                lineHeight = 41.sp,
                modifier = Modifier.weight(1f),
            )
            if (trailingPosition != null) {
                Text(
                    text = trailingPosition,
                    fontSize = 13.sp,
                    color = LabelTertiary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 8.dp, start = 12.dp),
                )
            }
        }
        if (subtitle != null) {
            Text(
                text = subtitle,
                fontSize = 15.sp,
                color = LabelSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
fun SmallTitleBar(
    title: String,
    onBack: (() -> Unit)? = null,
    rightContent: (@Composable () -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        if (onBack != null) {
            BackButton(onBack = onBack, haptic = haptic)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = title,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = Label,
            letterSpacing = (-0.2).sp,
            modifier = Modifier.weight(1f),
        )
        if (rightContent != null) rightContent()
    }
}

/**
 * Stage 3 polish 2 (#8): тап-зона 48×48dp вокруг back-стрелки.
 * Сама иконка 26dp, но clickable Box 48dp — палец легко попадает.
 */
@Composable
private fun BackButton(
    onBack: () -> Unit,
    haptic: HapticFeedback,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onBack()
            },
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Назад",
            tint = SystemBlue,
            modifier = Modifier.size(26.dp),
        )
    }
}

val ScreenInsets: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 4.dp)
