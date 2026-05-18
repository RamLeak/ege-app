package com.daniel.ege100.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daniel.ege100.ui.common.AppleCard
import com.daniel.ege100.ui.common.daysWord
import com.daniel.ege100.ui.theme.Label
import com.daniel.ege100.ui.theme.LabelSecondary
import com.daniel.ege100.ui.theme.LabelTertiary

/**
 * Phase 3 Stage B part Ж — превью карточка пробников.
 *
 * Полный календарь пробников будет в Stage P3-D с WorkManager-уведомлениями.
 * Здесь только информация «через N дней» и переход вперёд по тапу
 * (пока заглушка — навигация добавится в P3-D).
 */
@Composable
fun MockExamPreviewCard(daysUntilNext: Int, onClick: () -> Unit) {
    AppleCard(onClick = onClick, paddingDp = 22) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "📅", fontSize = 32.sp)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Следующий пробник",
                    fontSize = 13.sp,
                    color = LabelSecondary,
                )
                Spacer(Modifier.size(2.dp))
                Text(
                    text = if (daysUntilNext > 0) {
                        "Через $daysUntilNext ${daysWord(daysUntilNext)}"
                    } else if (daysUntilNext == 0) {
                        "Сегодня!"
                    } else {
                        "Будет назначен"
                    },
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Label,
                )
            }
            Text(
                text = "›",
                fontSize = 24.sp,
                color = LabelTertiary,
            )
        }
    }
}

/**
 * Phase 3 Stage B part Ж — простой расчёт дней до следующего пробника.
 *
 * Алгоритм (соответствует Safety Rule #3):
 *   - Первый пробник через 4 недели после `firstInstall` (= сегодня, если
 *     неизвестно когда установили).
 *   - Дальше каждые 3 недели.
 *   - Возвращаем дни до ближайшего пробника в будущем.
 *
 * Для P3-B мы пока не сохраняем install date — используем константу
 * «через 4 недели» (28 дней) от текущего дня. В P3-D добавим persistent
 * MockExamSchedule + WorkManager.
 */
fun daysUntilNextMock(firstInstallDay: Int? = null, todayDay: Long = java.time.LocalDate.now().toEpochDay()): Int {
    val firstDay = firstInstallDay?.toLong() ?: todayDay
    val firstMock = firstDay + 28  // первый через 4 недели
    if (todayDay <= firstMock) return (firstMock - todayDay).toInt()
    val intervalDays = 21L
    val sinceFirst = todayDay - firstMock
    val nextOffset = ((sinceFirst / intervalDays) + 1) * intervalDays
    return (firstMock + nextOffset - todayDay).toInt()
}
