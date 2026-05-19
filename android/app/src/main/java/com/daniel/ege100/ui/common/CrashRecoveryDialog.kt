package com.daniel.ege100.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.daniel.ege100.ui.theme.BgElevated
import com.daniel.ege100.ui.theme.Label
import com.daniel.ege100.ui.theme.LabelSecondary
import com.daniel.ege100.ui.theme.SystemBlue
import com.daniel.ege100.ui.theme.SystemRed

/**
 * Phase 4 Stage P4-D2 part Г (Convention #68) — диалог после восстановления
 * от краша. Показывается ОДИН раз при следующем запуске приложения после
 * того как [com.daniel.ege100.data.CrashLog.writeCrashReport] поставил
 * флаг `last_crash_unhandled`.
 *
 * Кнопки:
 *   - «📤 Отправить лог» — открывает share-sheet с файлом через FileProvider.
 *     После успешной отправки диалог закрывается (флаг уже сброшен caller'ом).
 *   - «Закрыть» — просто скрывает диалог, файл остаётся в filesDir/crashes/.
 *     Пользователь сможет отправить позже из Настроек.
 */
@Composable
fun CrashRecoveryDialog(
    onSend: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgElevated,
        title = {
            Text(
                text = "Приложение вылетело",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Label,
            )
        },
        text = {
            Text(
                text = "В прошлый раз произошла ошибка. Чтобы её исправить, отправь лог разработчику.",
                fontSize = 14.sp,
                color = LabelSecondary,
                lineHeight = 20.sp,
            )
        },
        confirmButton = {
            TextButton(onClick = onSend) {
                Text(
                    text = "📤 Отправить лог",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SystemBlue,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Закрыть",
                    fontSize = 15.sp,
                    color = SystemRed,
                )
            }
        },
    )
}
