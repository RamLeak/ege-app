package com.daniel.ege100.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daniel.ege100.data.Quote
import com.daniel.ege100.ui.common.AppleCard
import com.daniel.ege100.ui.theme.Label
import com.daniel.ege100.ui.theme.LabelSecondary
import com.daniel.ege100.ui.theme.SystemBlue

/**
 * Phase 3 Stage B part А — карточка «Цитата дня».
 *
 *   ❝
 *   Дорогу осилит идущий.
 *   — Античная мудрость
 *
 * Цитата выбирается детерминированно (epoch_day % size) — одна на день
 * для всех пользователей.
 */
@Composable
fun QuoteCard(quote: Quote, modifier: Modifier = Modifier) {
    AppleCard(modifier = modifier, paddingDp = 22) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "❝",
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold,
                color = SystemBlue.copy(alpha = 0.30f),
                lineHeight = 56.sp,
                modifier = Modifier.offset(y = (-12).dp),
            )
            Spacer(Modifier.height(0.dp))
            Text(
                text = quote.text,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = Label,
                lineHeight = 24.sp,
                fontStyle = FontStyle.Italic,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "— ${quote.author}",
                fontSize = 14.sp,
                color = LabelSecondary,
            )
        }
    }
}
