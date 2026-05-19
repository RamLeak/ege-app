package com.daniel.ege100.ui.profile

import android.content.Intent
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.daniel.ege100.ai.AiModel
import com.daniel.ege100.ai.AiProvider
import com.daniel.ege100.ai.AiProviderRegistry
import com.daniel.ege100.ai.AiProviderType
import com.daniel.ege100.ui.common.IosTextField
import com.daniel.ege100.ui.common.PrimaryButton
import com.daniel.ege100.ui.common.SecondaryButton
import com.daniel.ege100.ui.theme.BgElevated
import com.daniel.ege100.ui.theme.BgElevated2
import com.daniel.ege100.ui.theme.Label
import com.daniel.ege100.ui.theme.LabelSecondary
import com.daniel.ege100.ui.theme.LabelTertiary
import com.daniel.ege100.ui.theme.SystemBlue
import com.daniel.ege100.ui.theme.SystemGreen
import com.daniel.ege100.ui.theme.SystemGreenTint

/**
 * Phase 4 Stage A4 — bottom sheets для настроек AI.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderChooserBottomSheet(
    activeProvider: AiProviderType,
    onSelect: (AiProviderType) -> Unit,
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
            Text("AI провайдер", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Label)
            Spacer(Modifier.height(16.dp))
            AiProviderType.values().forEach { type ->
                ProviderRow(
                    provider = AiProviderRegistry.get(type),
                    selected = type == activeProvider,
                    onClick = { onSelect(type) },
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "💡 OpenRouter: 30+ моделей через один ключ, есть бесплатные.\n" +
                    "🔵 Google Gemini: 1500 запросов/день Gemini 2.0 Flash бесплатно.\n" +
                    "🟧 Claude direct: премиум-качество, требует оплаты.",
                fontSize = 12.sp,
                color = LabelSecondary,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ProviderRow(provider: AiProvider, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 6.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (selected) SystemBlue else Color.Transparent)
                .padding(if (!selected) 1.dp else 0.dp),
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = provider.displayName,
                fontSize = 16.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = Label,
            )
            Text(provider.description, fontSize = 12.sp, color = LabelSecondary, lineHeight = 16.sp)
        }
        if (selected) Text("✓", color = SystemBlue, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelChooserBottomSheet(
    provider: AiProvider,
    currentModelId: String,
    onSelect: (String) -> Unit,
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
            Text("Модель ${provider.displayName}", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Label)
            Spacer(Modifier.height(12.dp))
            provider.availableModels.forEach { model ->
                ModelRow(model, selected = model.id == currentModelId, onClick = { onSelect(model.id) })
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ModelRow(model: AiModel, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 6.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (selected) SystemBlue else Color.Transparent),
        ) {
            if (selected) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.White))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = model.displayName,
                    fontSize = 15.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = Label,
                )
                if (model.isFree) {
                    Spacer(Modifier.size(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(SystemGreenTint)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text("FREE", fontSize = 9.sp, color = SystemGreen, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Text(model.description, fontSize = 12.sp, color = LabelSecondary)
            Text(model.costHint, fontSize = 11.sp, color = LabelTertiary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeyEditBottomSheet(
    provider: AiProvider,
    currentKey: String?,
    onSave: (String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var value by remember { mutableStateOf(currentKey.orEmpty()) }
    var visible by remember { mutableStateOf(false) }
    val context = LocalContext.current

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
            Text("API ключ · ${provider.displayName}", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Label)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Получить ключ: ${provider.signupUrl}",
                fontSize = 13.sp,
                color = SystemBlue,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable {
                        val intent = Intent(Intent.ACTION_VIEW, provider.signupUrl.toUri())
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(intent) }
                    }
                    .padding(vertical = 4.dp, horizontal = 2.dp),
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IosTextField(
                    value = value,
                    onValueChange = { value = it.trim() },
                    placeholder = provider.keyHint,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.size(8.dp))
                IconButton(onClick = { visible = !visible }) {
                    Text(
                        text = if (visible) "🙈" else "👁",
                        fontSize = 22.sp,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                text = "Сохранить",
                onClick = { onSave(value.trim()) },
                enabled = value.trim().isNotBlank(),
            )
            if (currentKey != null) {
                Spacer(Modifier.height(10.dp))
                SecondaryButton(
                    text = "Удалить ключ",
                    onClick = onRemove,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(10.dp))
            SecondaryButton(
                text = "Отмена",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyLimitBottomSheet(
    current: Int,
    // Phase 4 Stage P4-C2 part В.2 (Convention #59) — показ usage + кнопка сброса.
    todayUsage: Int,
    onSave: (Int) -> Unit,
    onResetTodayUsage: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var value by remember { mutableStateOf(current) }
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
            Text("Лимит в день", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Label)
            Spacer(Modifier.height(8.dp))
            Text(
                "Это твой внутренний лимит (защита от случайных списаний на платных моделях). " +
                    "Лимит провайдера — отдельный, у OpenRouter free ≈ 200/день, у Gemini ≈ 1500/день.",
                fontSize = 13.sp,
                color = LabelSecondary,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(18.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(BgElevated2)
                    .padding(24.dp),
            ) {
                Text(
                    "$value",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = SystemBlue,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Сегодня использовано: $todayUsage из $current",
                fontSize = 13.sp,
                color = LabelSecondary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(10, 25, 50, 100, 200).forEach { preset ->
                    SecondaryButton(
                        text = "$preset",
                        onClick = { value = preset },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            PrimaryButton(
                text = "Сохранить",
                onClick = { onSave(value) },
                enabled = value != current,
            )
            Spacer(Modifier.height(10.dp))
            com.daniel.ege100.ui.common.TertiaryButton(
                text = "Сбросить счётчик сегодня",
                onClick = onResetTodayUsage,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            SecondaryButton(
                text = "Отмена",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}
