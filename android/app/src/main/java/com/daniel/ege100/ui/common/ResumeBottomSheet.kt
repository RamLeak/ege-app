package com.daniel.ege100.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daniel.ege100.ui.theme.BgElevated
import com.daniel.ege100.ui.theme.Label
import com.daniel.ege100.ui.theme.LabelSecondary
import kotlinx.coroutines.launch

/**
 * Stage 5 part А: Resume bottom sheet для тренажёров.
 *
 * Показывается при заходе в тренажёр, в котором есть сохранённый прогресс
 * (TrainerProgressStore.get != null). Две кнопки:
 *   - «Продолжить» (Primary, SystemBlue) — закрыть sheet с onResume.
 *   - «Начать сначала» (Secondary) — открыть второй sheet с подтверждением.
 *
 * При подтверждении сброса (DangerButton) — onStartOver. При отмене — снова
 * первый sheet (StartOver → null).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResumeBottomSheet(
    trainerTitle: String,
    savedPosition: Int,
    total: Int,
    onResume: () -> Unit,
    onStartOver: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var showConfirm by remember { mutableStateOf(false) }

    fun closeWith(action: () -> Unit) {
        scope.launch {
            try { sheetState.hide() } catch (_: Throwable) {}
            action()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = BgElevated,
        scrimColor = androidx.compose.ui.graphics.Color(0xCC000000),
        dragHandle = null,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp),
        ) {
            if (!showConfirm) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(androidx.compose.ui.graphics.Color(0x1F0A84FF)),
                ) {
                    Text("📍", fontSize = 32.sp)
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    text = trainerTitle,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Label,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Ты остановился на ${savedPosition + 1}-м слове из $total",
                    fontSize = 16.sp,
                    color = LabelSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(28.dp))
                PrimaryButton(
                    text = "Продолжить",
                    onClick = { closeWith(onResume) },
                )
                Spacer(Modifier.height(10.dp))
                SecondaryButton(
                    text = "Начать сначала",
                    onClick = { showConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(androidx.compose.ui.graphics.Color(0x26FF453A)),
                ) {
                    Text("⚠️", fontSize = 32.sp)
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "Уверен?",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Label,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Прогресс будет сброшен.\nСписок ошибок сохранится.",
                    fontSize = 15.sp,
                    color = LabelSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                )
                Spacer(Modifier.height(28.dp))
                DangerButton(
                    text = "Да, начать сначала",
                    onClick = { closeWith(onStartOver) },
                )
                Spacer(Modifier.height(10.dp))
                SecondaryButton(
                    text = "Отмена",
                    onClick = { showConfirm = false },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

