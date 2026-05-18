package com.daniel.ege100.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daniel.ege100.data.RuleEntry
import com.daniel.ege100.ui.theme.BgElevated
import com.daniel.ege100.ui.theme.Label
import com.daniel.ege100.ui.theme.LabelSecondary

/**
 * Stage 5 part Г — bottom sheet с правилом для типа задачи.
 *
 * Высота 85% экрана, заголовок «📋 Правило» + название типа, ниже —
 * SimpleMarkdownRenderer с прокруткой. Закрытие — свайпом вниз или
 * onDismiss.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleBottomSheet(
    rule: RuleEntry,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = BgElevated,
        scrimColor = androidx.compose.ui.graphics.Color(0xCC000000),
        modifier = Modifier.fillMaxHeight(0.88f),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = "📋 Правило",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = LabelSecondary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = rule.title,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Label,
                    letterSpacing = (-0.4).sp,
                    lineHeight = 34.sp,
                )
                Spacer(Modifier.height(20.dp))
                SimpleMarkdownRenderer(rule.markdown)
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}
