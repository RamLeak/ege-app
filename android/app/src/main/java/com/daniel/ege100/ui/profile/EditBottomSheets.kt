package com.daniel.ege100.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daniel.ege100.ui.common.IosTextField
import com.daniel.ege100.ui.common.PrimaryButton
import com.daniel.ege100.ui.common.SecondaryButton
import com.daniel.ege100.ui.theme.BgElevated
import com.daniel.ege100.ui.theme.BgElevated2
import com.daniel.ege100.ui.theme.Label
import com.daniel.ege100.ui.theme.LabelSecondary
import com.daniel.ege100.ui.theme.LabelTertiary
import com.daniel.ege100.ui.theme.SystemBlue
import com.daniel.ege100.ui.theme.SystemBlueTint
import java.time.LocalDate
import java.time.YearMonth

/**
 * Phase 3 Stage A part В — bottom sheets для редактирования полей профиля.
 *
 * Структура одинаковая: RoundedCornerShape(top=28dp), фон BgElevated, padding
 * 24dp. PrimaryButton «Сохранить» (disabled если ничего не изменилось) +
 * SecondaryButton «Отмена».
 *
 * DatePicker — собственная iOS-style wheel-реализация (3 LazyColumn-а). Не
 * пытаемся делать snap-скролл (сложно сделать качественно в Compose без
 * accompanist) — пользователь крутит список + тапает на нужное значение.
 * Выбранное значение жирно, остальные приглушены.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditNameBottomSheet(
    current: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
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
            Text(
                text = "Имя",
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = Label,
            )
            Spacer(Modifier.height(16.dp))
            IosTextField(
                value = value,
                onValueChange = { value = it.take(40) },
                placeholder = "Введи имя",
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done,
                ),
            )
            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                text = "Сохранить",
                onClick = { onSave(value.trim()) },
                enabled = value.trim().isNotBlank() && value.trim() != current,
            )
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
fun EditScoreBottomSheet(
    current: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var score by remember { mutableStateOf(current.coerceIn(50, 100)) }
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
                text = "Целевой балл",
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = Label,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "От 50 до 100",
                fontSize = 14.sp,
                color = LabelSecondary,
            )
            Spacer(Modifier.height(20.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(BgElevated2)
                    .padding(24.dp),
            ) {
                Text(
                    text = score.toString(),
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Bold,
                    color = SystemBlue,
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                StepperButton(
                    text = "−",
                    enabled = score > 50,
                    onClick = { score = (score - 1).coerceAtLeast(50) },
                    modifier = Modifier.weight(1f),
                )
                StepperButton(
                    text = "−5",
                    enabled = score > 50,
                    onClick = { score = (score - 5).coerceAtLeast(50) },
                    modifier = Modifier.weight(1f),
                )
                StepperButton(
                    text = "+5",
                    enabled = score < 100,
                    onClick = { score = (score + 5).coerceAtMost(100) },
                    modifier = Modifier.weight(1f),
                )
                StepperButton(
                    text = "+",
                    enabled = score < 100,
                    onClick = { score = (score + 1).coerceAtMost(100) },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(24.dp))
            PrimaryButton(
                text = "Сохранить",
                onClick = { onSave(score) },
                enabled = score != current,
            )
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

@Composable
private fun StepperButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) SystemBlueTint else Color.Transparent)
            .clickable(enabled = enabled) { onClick() },
    ) {
        Text(
            text = text,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) SystemBlue else LabelTertiary,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditDateBottomSheet(
    title: String,
    current: LocalDate,
    minYear: Int,
    maxYear: Int,
    onDismiss: () -> Unit,
    onSave: (LocalDate) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var year by remember { mutableStateOf(current.year.coerceIn(minYear, maxYear)) }
    var month by remember { mutableStateOf(current.monthValue) }
    var day by remember { mutableStateOf(current.dayOfMonth) }
    val daysInMonth = YearMonth.of(year, month).lengthOfMonth()
    LaunchedEffect(daysInMonth) { if (day > daysInMonth) day = daysInMonth }

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
                text = title,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = Label,
            )
            Spacer(Modifier.height(20.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
            ) {
                NumberWheel(
                    values = (1..daysInMonth).toList(),
                    selected = day,
                    onSelect = { day = it },
                    formatter = { it.toString().padStart(2, '0') },
                    modifier = Modifier.weight(0.8f),
                )
                NumberWheel(
                    values = (1..12).toList(),
                    selected = month,
                    onSelect = { month = it },
                    formatter = { MONTH_NAMES[it - 1] },
                    modifier = Modifier.weight(1.4f),
                )
                NumberWheel(
                    values = (minYear..maxYear).toList(),
                    selected = year,
                    onSelect = { year = it },
                    formatter = { it.toString() },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                text = "Сохранить",
                onClick = {
                    val safeDay = day.coerceAtMost(YearMonth.of(year, month).lengthOfMonth())
                    onSave(LocalDate.of(year, month, safeDay))
                },
            )
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

private val MONTH_NAMES = listOf(
    "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
    "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь",
)

/**
 * Простой iOS-style вертикальный список: LazyColumn с тап-выбором.
 * Выбранное значение SystemBlue 18sp SemiBold, остальные LabelSecondary 17sp.
 * При первой композиции прокручиваем список, чтобы выбранный был виден.
 */
@Composable
private fun NumberWheel(
    values: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
    formatter: (Int) -> String,
    modifier: Modifier = Modifier,
) {
    val initialIndex = values.indexOf(selected).coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)

    LaunchedEffect(selected) {
        val idx = values.indexOf(selected)
        if (idx >= 0 && (idx < listState.firstVisibleItemIndex ||
                idx > listState.firstVisibleItemIndex + listState.layoutInfo.visibleItemsInfo.size - 1)) {
            listState.animateScrollToItem(idx.coerceAtLeast(0))
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(BgElevated2),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(values, key = { it }) { v ->
                val isSelected = v == selected
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clickable { onSelect(v) },
                ) {
                    Text(
                        text = formatter(v),
                        fontSize = if (isSelected) 18.sp else 17.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) SystemBlue else LabelSecondary,
                    )
                }
            }
        }
    }
}
