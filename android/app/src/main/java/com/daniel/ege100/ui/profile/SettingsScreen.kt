package com.daniel.ege100.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import com.daniel.ege100.ui.common.SmoothLazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daniel.ege100.ai.AiProviderRegistry
import com.daniel.ege100.ai.AiProviderType
import android.widget.Toast
import com.daniel.ege100.data.AiSettings
import com.daniel.ege100.data.AiSettingsStore
import com.daniel.ege100.data.AppSettings
import com.daniel.ege100.data.AppSettingsStore
import com.daniel.ege100.data.CrashLog
import com.daniel.ege100.data.RadarStyle
import com.daniel.ege100.data.SecureKeyStore
import com.daniel.ege100.data.ThemeMode
import com.daniel.ege100.ui.common.AppleCard
import com.daniel.ege100.ui.common.AppleListRow
import com.daniel.ege100.ui.common.DangerButton
import com.daniel.ege100.ui.common.LargeTitleBar
import com.daniel.ege100.ui.common.PrimaryButton
import com.daniel.ege100.ui.common.SecondaryButton
import com.daniel.ege100.ui.theme.Bg
import com.daniel.ege100.ui.theme.BgElevated
import com.daniel.ege100.ui.theme.Label
import com.daniel.ege100.ui.theme.LabelSecondary
import com.daniel.ege100.ui.theme.LabelTertiary
import com.daniel.ege100.ui.theme.SystemBlue
import com.daniel.ege100.ui.theme.SystemBlueTint
import com.daniel.ege100.ui.theme.SystemRed
import com.daniel.ege100.ui.theme.SystemRedTint
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
    onResetClick: () -> Unit,
) {
    val context = LocalContext.current
    val settingsFlow = remember(context) { AppSettingsStore.settingsFlow(context) }
    val settings by settingsFlow.collectAsState(initial = AppSettings())
    val aiSettingsFlow = remember(context) { AiSettingsStore.settingsFlow(context) }
    val aiSettings by aiSettingsFlow.collectAsState(initial = AiSettings())
    val keyStore = remember(context) { SecureKeyStore(context) }
    var hasKeyTick by remember { mutableStateOf(0) }  // re-trigger hasKey() после save/remove
    val scope = rememberCoroutineScope()
    var showThemeSheet by remember { mutableStateOf(false) }
    var showRadarSheet by remember { mutableStateOf(false) }
    var showProviderSheet by remember { mutableStateOf(false) }
    var showModelSheet by remember { mutableStateOf(false) }
    var showKeySheet by remember { mutableStateOf(false) }
    var showLimitSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            LargeTitleBar(title = "Настройки", subtitle = "Тема, уведомления, данные", onBack = onBack)
        },
        containerColor = Bg,
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(contentPadding),
        ) {
            SmoothLazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item("appearance_title") { SectionTitle("Внешний вид") }
                item("appearance") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppleListRow(
                            title = "Тема",
                            subtitle = themeLabel(settings.themeMode),
                            leadingEmoji = "🎨",
                            leadingTint = SystemBlueTint,
                            onClick = { showThemeSheet = true },
                        )
                        AppleListRow(
                            title = "Радар",
                            subtitle = radarLabel(settings.radarStyle),
                            leadingEmoji = "📊",
                            leadingTint = SystemBlueTint,
                            onClick = { showRadarSheet = true },
                        )
                    }
                }
                item("notify_title") { SectionTitle("Уведомления") }
                item("notify") {
                    AppleCard(paddingDp = 4) {
                        Column {
                            SwitchRow(
                                emoji = "🔔",
                                title = "Пробники",
                                subtitle = "Stage P3-D",
                                checked = settings.notifyMockExams,
                                onChange = { v ->
                                    scope.launch { AppSettingsStore.setNotifyMockExams(context, v) }
                                },
                            )
                            SwitchRow(
                                emoji = "🔥",
                                title = "Streak",
                                subtitle = "Stage P3-D",
                                checked = settings.notifyStreak,
                                onChange = { v ->
                                    scope.launch { AppSettingsStore.setNotifyStreak(context, v) }
                                },
                            )
                            SwitchRow(
                                emoji = "📚",
                                title = "Напоминания",
                                subtitle = "Stage P3-D",
                                checked = settings.notifyReminders,
                                onChange = { v ->
                                    scope.launch { AppSettingsStore.setNotifyReminders(context, v) }
                                },
                            )
                        }
                    }
                }
                item("ai_title") { SectionTitle("AI помощник") }
                item("ai") {
                    val activeProvider = AiProviderRegistry.get(aiSettings.activeProvider)
                    val currentModelId = aiSettings.modelFor(aiSettings.activeProvider)
                    val currentModel = activeProvider.availableModels.firstOrNull { it.id == currentModelId }
                    val keyStatus = remember(hasKeyTick, aiSettings.activeProvider) {
                        keyStore.hasKey(aiSettings.activeProvider.name)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppleListRow(
                            title = "Провайдер",
                            subtitle = activeProvider.displayName,
                            leadingEmoji = "🤖",
                            leadingTint = SystemBlueTint,
                            onClick = { showProviderSheet = true },
                        )
                        AppleListRow(
                            title = "Модель",
                            subtitle = (currentModel?.displayName ?: currentModelId) +
                                (if (currentModel?.isFree == true) " · бесплатно" else ""),
                            leadingEmoji = "🧠",
                            leadingTint = SystemBlueTint,
                            onClick = { showModelSheet = true },
                        )
                        AppleListRow(
                            title = "API ключ",
                            subtitle = if (keyStatus) "Сохранён ✓" else "Не задан",
                            leadingEmoji = "🔑",
                            leadingTint = SystemBlueTint,
                            onClick = { showKeySheet = true },
                        )
                        AppleListRow(
                            title = "Лимит в день",
                            subtitle = "${aiSettings.dailyLimit} запросов · сегодня ${aiSettings.todayUsage}",
                            leadingEmoji = "💵",
                            leadingTint = SystemBlueTint,
                            onClick = { showLimitSheet = true },
                        )
                    }
                }
                // Phase 4 Stage P4-C2 part Б (Convention #58) — toggle для
                // кнопок букв в №9-12 (default ON — лучший mobile UX).
                item("trainers_title") { SectionTitle("Тренажёры") }
                item("trainers") {
                    AppleCard(paddingDp = 4) {
                        SwitchRow(
                            emoji = "🔤",
                            title = "Кнопки выбора букв",
                            subtitle = "В тренажёрах № 9-12 русского — вместо ручного ввода",
                            checked = settings.useLetterChoices,
                            onChange = { v ->
                                scope.launch { AppSettingsStore.setUseLetterChoices(context, v) }
                            },
                        )
                    }
                }
                item("data_title") { SectionTitle("Данные") }
                item("data") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppleListRow(
                            title = "Экспорт прогресса",
                            subtitle = "В Telegram, Drive или файл",
                            leadingEmoji = "📤",
                            leadingTint = SystemBlueTint,
                            onClick = onExportClick,
                        )
                        AppleListRow(
                            title = "Импорт прогресса",
                            subtitle = "Из файла резервной копии",
                            leadingEmoji = "📥",
                            leadingTint = SystemBlueTint,
                            onClick = onImportClick,
                        )
                        AppleListRow(
                            title = "Сброс прогресса",
                            subtitle = "Удалить тренажёры и избранное",
                            leadingEmoji = "🗑️",
                            leadingTint = SystemRedTint,
                            onClick = onResetClick,
                        )
                    }
                }
                // Phase 4 Stage P4-D2 part Г (Convention #68) — поддержка и
                // отчёты о крашах. Кнопка показывает счётчик имеющихся логов;
                // нет → подсказка серым, есть → SystemBlueTint и share-sheet
                // отправляет самый свежий файл.
                item("support_title") { SectionTitle("Поддержка") }
                item("support") {
                    val crashFiles = remember(settings) { CrashLog.listFiles(context) }
                    val hasCrashes = crashFiles.isNotEmpty()
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppleListRow(
                            title = "Отправить crash log",
                            subtitle = if (hasCrashes)
                                "Найдено ${crashFiles.size} ${crashLogWord(crashFiles.size)} — отправь разработчику"
                            else
                                "Логи отсутствуют",
                            leadingEmoji = "🐛",
                            leadingTint = if (hasCrashes) SystemRedTint else SystemBlueTint,
                            onClick = {
                                if (hasCrashes) {
                                    val ok = CrashLog.shareLatest(context)
                                    if (!ok) Toast.makeText(
                                        context,
                                        "Не удалось открыть share-sheet",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Пока что не было крашей — здорово!",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            },
                        )
                    }
                }
                item("footer_pad") { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    if (showThemeSheet) {
        ThemeBottomSheet(
            current = settings.themeMode,
            onSelect = { newMode ->
                scope.launch { AppSettingsStore.setThemeMode(context, newMode) }
                showThemeSheet = false
            },
            onDismiss = { showThemeSheet = false },
        )
    }
    if (showRadarSheet) {
        RadarBottomSheet(
            current = settings.radarStyle,
            onSelect = { newStyle ->
                scope.launch { AppSettingsStore.setRadarStyle(context, newStyle) }
                showRadarSheet = false
            },
            onDismiss = { showRadarSheet = false },
        )
    }
    if (showProviderSheet) {
        ProviderChooserBottomSheet(
            activeProvider = aiSettings.activeProvider,
            onSelect = { type ->
                scope.launch { AiSettingsStore.setActiveProvider(context, type) }
                showProviderSheet = false
            },
            onDismiss = { showProviderSheet = false },
        )
    }
    if (showModelSheet) {
        ModelChooserBottomSheet(
            provider = AiProviderRegistry.get(aiSettings.activeProvider),
            currentModelId = aiSettings.modelFor(aiSettings.activeProvider),
            onSelect = { modelId ->
                scope.launch { AiSettingsStore.setModelFor(context, aiSettings.activeProvider, modelId) }
                showModelSheet = false
            },
            onDismiss = { showModelSheet = false },
        )
    }
    if (showKeySheet) {
        val activeProvider = AiProviderRegistry.get(aiSettings.activeProvider)
        val currentKey = keyStore.getKey(aiSettings.activeProvider.name)
        ApiKeyEditBottomSheet(
            provider = activeProvider,
            currentKey = currentKey,
            onSave = { key ->
                keyStore.saveKey(aiSettings.activeProvider.name, key)
                hasKeyTick++
                showKeySheet = false
            },
            onRemove = {
                keyStore.removeKey(aiSettings.activeProvider.name)
                hasKeyTick++
                showKeySheet = false
            },
            onDismiss = { showKeySheet = false },
        )
    }
    if (showLimitSheet) {
        DailyLimitBottomSheet(
            current = aiSettings.dailyLimit,
            todayUsage = aiSettings.todayUsage,
            onSave = { newLimit ->
                scope.launch { AiSettingsStore.setDailyLimit(context, newLimit) }
                showLimitSheet = false
            },
            onResetTodayUsage = {
                scope.launch { AiSettingsStore.resetTodayUsage(context) }
                showLimitSheet = false
            },
            onDismiss = { showLimitSheet = false },
        )
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
private fun SwitchRow(
    emoji: String,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(SystemBlueTint),
        ) {
            Text(emoji, fontSize = 22.sp)
        }
        Spacer(Modifier.size(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Label)
            Text(subtitle, fontSize = 13.sp, color = LabelSecondary, modifier = Modifier.padding(top = 2.dp))
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = SystemBlue,
                checkedThumbColor = Color.White,
            ),
        )
    }
}

private fun crashLogWord(n: Int): String {
    val mod10 = n % 10
    val mod100 = n % 100
    return when {
        mod10 == 1 && mod100 != 11 -> "лог"
        mod10 in 2..4 && mod100 !in 12..14 -> "лога"
        else -> "логов"
    }
}

private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.AUTO -> "Авто (по системе)"
    ThemeMode.DARK -> "Тёмная"
    ThemeMode.LIGHT -> "Светлая"
}

private fun radarLabel(style: RadarStyle): String = when (style) {
    RadarStyle.LIST -> "Список с прогресс-барами"
    RadarStyle.DONUT -> "Круговая диаграмма"
    RadarStyle.HEATMAP -> "Тепловая карта"
    RadarStyle.RADAR_CHART -> "Лепестковая диаграмма"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeBottomSheet(
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
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
                text = "Тема",
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = Label,
            )
            Spacer(Modifier.height(16.dp))
            ThemeMode.values().forEach { mode ->
                RadioRow(
                    title = themeLabel(mode),
                    selected = mode == current,
                    onClick = { onSelect(mode) },
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RadarBottomSheet(
    current: RadarStyle,
    onSelect: (RadarStyle) -> Unit,
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
                text = "Внешний вид радара",
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = Label,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Появится на главном экране в Stage P3-B",
                fontSize = 14.sp,
                color = LabelSecondary,
            )
            Spacer(Modifier.height(16.dp))
            RadarStyle.values().forEach { style ->
                RadioRow(
                    title = radarLabel(style),
                    selected = style == current,
                    onClick = { onSelect(style) },
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun RadioRow(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 14.dp, horizontal = 4.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (selected) SystemBlue else Color.Transparent)
                .border(
                    width = 1.5.dp,
                    color = if (selected) Color.Transparent else LabelTertiary,
                    shape = CircleShape,
                ),
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
        Spacer(Modifier.size(14.dp))
        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = Label,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Text("✓", color = SystemBlue, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Phase 3 Stage A part Д — bottom sheet подтверждения сброса прогресса.
 * Используется отдельно из ProgressResetFlow (см. EgeApp.kt).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResetProgressBottomSheet(
    onConfirm: () -> Unit,
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
                text = "⚠️ Сбросить весь прогресс?",
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = Label,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Будет удалено:\n• Прогресс всех тренажёров\n• Избранные задачи\n• История ошибок",
                fontSize = 15.sp,
                color = LabelSecondary,
                lineHeight = 22.sp,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Профиль и настройки сохранятся.",
                fontSize = 14.sp,
                color = LabelTertiary,
            )
            Spacer(Modifier.height(24.dp))
            DangerButton(
                text = "Да, сбросить",
                onClick = onConfirm,
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

/**
 * Bottom sheet подтверждения импорта — «заменить текущий прогресс».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportConfirmBottomSheet(
    backupDate: String,
    onConfirm: () -> Unit,
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
                text = "Заменить прогресс?",
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = Label,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Файл от $backupDate. Текущий прогресс будет потерян.",
                fontSize = 15.sp,
                color = LabelSecondary,
                lineHeight = 22.sp,
            )
            Spacer(Modifier.height(24.dp))
            PrimaryButton(
                text = "Заменить",
                onClick = onConfirm,
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
