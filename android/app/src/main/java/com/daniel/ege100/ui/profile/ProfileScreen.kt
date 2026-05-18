package com.daniel.ege100.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daniel.ege100.data.UserProfile
import com.daniel.ege100.data.UserProfileStore
import com.daniel.ege100.ui.common.AppleCard
import com.daniel.ege100.ui.common.AppleListRow
import com.daniel.ege100.ui.common.LargeTitleBar
import com.daniel.ege100.ui.theme.Bg
import com.daniel.ege100.ui.theme.Label
import com.daniel.ege100.ui.theme.LabelSecondary
import com.daniel.ege100.ui.theme.LabelTertiary
import com.daniel.ege100.ui.theme.SystemBlue
import com.daniel.ege100.ui.theme.SystemBlueTint
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

private enum class EditField { Name, BirthDate, TargetScore, ExamDate }

/**
 * Phase 3 Stage A part В — экран Профиль.
 *
 * Раздел 1: аватар + имя + «Дни до ЕГЭ».
 * Раздел 2: личные данные (4 строки, тап → bottom sheet редактирования).
 * Раздел 3: «Подготовка» — Настройки + Импорт/Экспорт (последние 2 — переход в Settings).
 * Раздел 4: О приложении — версия, GitHub (отключен пока).
 */
@Composable
fun ProfileScreen(
    contentPadding: PaddingValues,
    onSettingsClick: () -> Unit,
    onImportClick: () -> Unit,
    onExportClick: () -> Unit,
) {
    val context = LocalContext.current
    val profileFlow = remember(context) { UserProfileStore.profileFlow(context) }
    val profile by profileFlow.collectAsState(initial = UserProfile())
    val scope = rememberCoroutineScope()
    var editing: EditField? by remember { mutableStateOf(null) }

    Scaffold(
        topBar = { LargeTitleBar(title = "Профиль", subtitle = "Твои данные и настройки") },
        containerColor = Bg,
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(contentPadding),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item("header") { HeaderCard(profile) }
                item("personal_title") { SectionTitle("Личные данные") }
                item("personal") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppleListRow(
                            title = "Имя",
                            subtitle = if (profile.name.isBlank()) "Не задано" else profile.name,
                            leadingEmoji = "✍️",
                            leadingTint = SystemBlueTint,
                            onClick = { editing = EditField.Name },
                        )
                        AppleListRow(
                            title = "Дата рождения",
                            subtitle = profile.birthDateParsed?.format(DATE_FORMAT) ?: "Не задана",
                            leadingEmoji = "🎂",
                            leadingTint = SystemBlueTint,
                            onClick = { editing = EditField.BirthDate },
                        )
                        AppleListRow(
                            title = "Целевой балл",
                            subtitle = "${profile.targetScore} из 100",
                            leadingEmoji = "🎯",
                            leadingTint = SystemBlueTint,
                            onClick = { editing = EditField.TargetScore },
                        )
                        AppleListRow(
                            title = "Дата ЕГЭ",
                            subtitle = profile.examDateParsed.format(DATE_FORMAT),
                            leadingEmoji = "📅",
                            leadingTint = SystemBlueTint,
                            onClick = { editing = EditField.ExamDate },
                        )
                    }
                }
                item("prep_title") { SectionTitle("Подготовка") }
                item("prep") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppleListRow(
                            title = "Настройки",
                            subtitle = "Тема, радар, уведомления, сброс",
                            leadingEmoji = "⚙️",
                            leadingTint = SystemBlueTint,
                            onClick = onSettingsClick,
                        )
                        AppleListRow(
                            title = "Импорт прогресса",
                            subtitle = "Восстановить из JSON-файла",
                            leadingEmoji = "📥",
                            leadingTint = SystemBlueTint,
                            onClick = onImportClick,
                        )
                        AppleListRow(
                            title = "Экспорт прогресса",
                            subtitle = "Сохранить в Telegram/Drive",
                            leadingEmoji = "📤",
                            leadingTint = SystemBlueTint,
                            onClick = onExportClick,
                        )
                    }
                }
                item("about_title") { SectionTitle("О приложении") }
                item("about") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppleListRow(
                            title = "Версия",
                            subtitle = "0.1.0 (Phase 3 Stage A)",
                            leadingEmoji = "📦",
                            leadingTint = SystemBlueTint,
                            trailing = null,
                            onClick = {},
                        )
                    }
                }
                item("footer_pad") { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    when (editing) {
        EditField.Name -> EditNameBottomSheet(
            current = profile.name,
            onDismiss = { editing = null },
            onSave = { newName ->
                scope.launch { UserProfileStore.setName(context, newName) }
                editing = null
            },
        )
        EditField.BirthDate -> EditDateBottomSheet(
            title = "Дата рождения",
            current = profile.birthDateParsed ?: LocalDate.of(2007, 1, 1),
            minYear = 1990,
            maxYear = LocalDate.now().year,
            onDismiss = { editing = null },
            onSave = { d ->
                scope.launch { UserProfileStore.setBirthDate(context, d) }
                editing = null
            },
        )
        EditField.TargetScore -> EditScoreBottomSheet(
            current = profile.targetScore,
            onDismiss = { editing = null },
            onSave = { score ->
                scope.launch { UserProfileStore.setTargetScore(context, score) }
                editing = null
            },
        )
        EditField.ExamDate -> EditDateBottomSheet(
            title = "Дата ЕГЭ",
            current = profile.examDateParsed,
            minYear = LocalDate.now().year,
            maxYear = LocalDate.now().year + 5,
            onDismiss = { editing = null },
            onSave = { d ->
                scope.launch { UserProfileStore.setExamDate(context, d) }
                editing = null
            },
        )
        null -> Unit
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = LabelTertiary,
        modifier = Modifier.padding(start = 4.dp),
    )
}

@Composable
private fun HeaderCard(profile: UserProfile) {
    AppleCard(paddingDp = 24) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(SystemBlueTint),
            ) {
                if (profile.initial != null) {
                    Text(
                        text = profile.initial!!,
                        color = SystemBlue,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    Text(text = "👤", fontSize = 42.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = profile.name.ifBlank { "Без имени" },
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = Label,
            )
            Spacer(Modifier.height(2.dp))
            val days = profile.daysUntilExam()
            val daysText = when {
                days > 0 -> "До ЕГЭ-${profile.examDateParsed.year} осталось $days ${daysWord(days)}"
                days == 0 -> "ЕГЭ сегодня!"
                else -> "ЕГЭ был ${-days} ${daysWord(-days)} назад"
            }
            Text(
                text = daysText,
                fontSize = 14.sp,
                color = LabelSecondary,
            )
        }
    }
}

private fun daysWord(n: Int): String {
    val n100 = n % 100
    val n10 = n % 10
    return when {
        n100 in 11..14 -> "дней"
        n10 == 1 -> "день"
        n10 in 2..4 -> "дня"
        else -> "дней"
    }
}
