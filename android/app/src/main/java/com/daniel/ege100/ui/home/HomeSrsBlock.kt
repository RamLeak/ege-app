package com.daniel.ege100.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daniel.ege100.ui.common.AppleCard
import com.daniel.ege100.ui.common.PrimaryButton
import com.daniel.ege100.ui.common.cardsWord
import com.daniel.ege100.ui.theme.Label
import com.daniel.ege100.ui.theme.LabelSecondary
import com.daniel.ege100.ui.theme.SystemBlue
import com.daniel.ege100.ui.theme.SystemOrange

/**
 * Phase 5 Stage E3 (§1.4) — карточка «Повторение на сегодня» на главном
 * экране. Размещается после Радара. Если `count == 0` — НЕ показывается
 * (return-guard в HomeScreen, ниже на уровне item).
 *
 * Структура:
 *   ┌──────────────────────────────────┐
 *   │ 📚 Повторение на сегодня          │
 *   │                                  │
 *   │         12 карточек              │
 *   │                                  │
 *   │       [Начать повторение]        │
 *   └──────────────────────────────────┘
 *
 * Streak отображение добавится в E4 (SrsStreakManager).
 */
@Composable
fun HomeSrsBlock(
    dueCount: Int,
    streak: Int = 0,
    onStart: () -> Unit,
) {
    AppleCard(paddingDp = 22) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "📚", fontSize = 24.sp)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Повторение на сегодня",
                    fontSize = 15.sp,
                    color = LabelSecondary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                // Phase 5 Stage E4 (§1.7) — SRS streak в шапке блока.
                if (streak >= 1) {
                    Text(
                        text = "🔥 $streak",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SystemOrange,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = dueCount.toString(),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = SystemBlue,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = cardsWord(dueCount),
                    fontSize = 17.sp,
                    color = Label,
                )
            }
            Spacer(Modifier.height(18.dp))
            PrimaryButton(
                text = "Начать повторение",
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
