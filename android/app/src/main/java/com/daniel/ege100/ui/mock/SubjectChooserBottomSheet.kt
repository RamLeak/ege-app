package com.daniel.ege100.ui.mock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daniel.ege100.ui.common.AppleCard
import com.daniel.ege100.ui.theme.BgElevated
import com.daniel.ege100.ui.theme.Label
import com.daniel.ege100.ui.theme.LabelSecondary
import com.daniel.ege100.ui.theme.LabelTertiary
import com.daniel.ege100.ui.theme.SystemBlueTint

/**
 * Phase 4 Stage A1 — выбор предмета пробника.
 *
 * Конвенция #42: пробник = по одной задаче из каждого типа предмета.
 * Math: 19 типов, Rus: 26 типов (без сочинения №27).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectChooserBottomSheet(
    onMathChosen: () -> Unit,
    onRusChosen: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = BgElevated,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp),
        ) {
            Text(
                text = "Выбери предмет",
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = Label,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "По одной задаче из каждого типа предмета",
                fontSize = 14.sp,
                color = LabelSecondary,
            )
            Spacer(Modifier.height(20.dp))
            SubjectChoiceCard(
                emoji = "📐",
                title = "Математика профильная",
                subtitle = "19 заданий · из типов №1-19",
                hint = "~2-3 часа в реальности",
                onClick = onMathChosen,
            )
            Spacer(Modifier.height(12.dp))
            SubjectChoiceCard(
                emoji = "✍️",
                title = "Русский язык",
                subtitle = "26 заданий · из типов №1-26",
                hint = "Без сочинения (№27 нужна проверка человеком)",
                onClick = onRusChosen,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SubjectChoiceCard(
    emoji: String,
    title: String,
    subtitle: String,
    hint: String,
    onClick: () -> Unit,
) {
    AppleCard(onClick = onClick, paddingDp = 18) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(SystemBlueTint),
            ) {
                Text(text = emoji, fontSize = 28.sp)
            }
            Spacer(Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Label)
                Text(text = subtitle, fontSize = 13.sp, color = LabelSecondary)
                Spacer(Modifier.height(2.dp))
                Text(text = hint, fontSize = 11.sp, color = LabelTertiary)
            }
            Text("›", fontSize = 22.sp, color = LabelTertiary)
        }
    }
}
