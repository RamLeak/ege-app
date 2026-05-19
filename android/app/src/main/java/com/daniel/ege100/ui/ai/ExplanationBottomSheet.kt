package com.daniel.ege100.ui.ai

import android.app.Application
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daniel.ege100.ai.AiProviderRegistry
import com.daniel.ege100.ai.AiResponse
import com.daniel.ege100.ai.LatexCleaner
import com.daniel.ege100.data.AiResponseCacheEntity
import com.daniel.ege100.data.AiSettingsStore
import com.daniel.ege100.data.EgeDatabase
import com.daniel.ege100.data.SecureKeyStore
import com.daniel.ege100.data.UserDataDatabase
import com.daniel.ege100.ui.common.PrimaryButton
import com.daniel.ege100.ui.common.SimpleMarkdownRenderer
import com.daniel.ege100.ui.theme.BgElevated
import com.daniel.ege100.ui.theme.BgElevated2
import com.daniel.ege100.ui.theme.Label
import com.daniel.ege100.ui.theme.LabelSecondary
import com.daniel.ege100.ui.theme.LabelTertiary
import com.daniel.ege100.ui.theme.SystemBlue
import com.daniel.ege100.ui.theme.SystemOrange
import com.daniel.ege100.ui.theme.SystemRed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Phase 4 Stage P4-D (Convention #71) → P4-D6 redesign (Convention #88).
 *
 * Структура:
 *   - Кастомный drag handle сверху (тёмная iOS-капсула 40×4dp).
 *   - Шапка: 📖 + слово (headline) + inline SourceBadge («✨ Opus 4.7») | ✕.
 *   - Pill-style TabRow с `animateColorAsState` для smooth подсветки активного.
 *   - Контент: `heightIn(min=140.dp, max=400.dp)` + verticalScroll.
 *   - `windowInsetsPadding(WindowInsets.navigationBars)` гарантирует что нижняя
 *     часть не залазит под navigation bar системы.
 *   - НЕТ нижней кнопки «Закрыть» — закрытие через handle / крестик / тап вне.
 *
 * Алгоритм load() остался прежним (P4-D5 + #87):
 *   1. Pre-gen из corpus.db (`TrainerExplanationDao.get(word, kind)`).
 *      Source = "pre_gen" → badge «✨ Opus 4.7».
 *   2. Online cache в user_data.db по sha256(provider|model|EXPL|word|kind|ctx).
 *      Source = "online_cached".
 *   3. Online AI запрос с JSON-промптом → парсинг → кеширование. При неудаче
 *      JSON-парсинга весь текст кладётся в `explanation` (source = "online_ai_raw").
 *   4. Если нет API ключа — error message с CTA на Настройки.
 */
enum class ExplanationTab(val shortTitle: String, val fullTitle: String) {
    WHY("Почему", "Почему именно так"),
    RULE("Правило", "Правило ЕГЭ"),
    EXAMPLES("Примеры", "Похожие примеры"),
    MNEMONIC("Запомнить", "Запоминалка"),
}

data class ExplanationUi(
    val isLoading: Boolean = true,
    val explanation: String = "",
    val rule: String = "",
    val examples: String = "",
    val mnemonic: String = "",
    val source: String = "",
    val error: String? = null,
    val errorIsAuth: Boolean = false,
)

class ExplanationViewModel(app: Application) : AndroidViewModel(app) {
    private val explanationDao = EgeDatabase.get(app).trainerExplanationDao()
    private val aiCacheDao = UserDataDatabase.get(app).aiResponseCacheDao()
    private val _state = MutableStateFlow(ExplanationUi())
    val state: StateFlow<ExplanationUi> = _state.asStateFlow()

    fun load(word: String, kind: String, fallbackContext: String) {
        viewModelScope.launch {
            try {
                loadImpl(word, kind, fallbackContext)
            } catch (e: Throwable) {
                android.util.Log.e("Explanation", "load() crash", e)
                _state.value = ExplanationUi(
                    isLoading = false,
                    error = "Внутренняя ошибка: ${e.message?.take(120) ?: e.javaClass.simpleName}",
                )
            }
        }
    }

    private suspend fun loadImpl(word: String, kind: String, fallbackContext: String) {
        val ctx = getApplication<Application>()
        com.daniel.ege100.data.BreadcrumbLog.add(
            "Explanation.load: word='${word.take(40)}', kind=$kind",
        )

        // Phase 4 Stage P4-D5 (Convention #86) — диагностика.
        val countAll = runCatching { explanationDao.countAll() }.getOrNull() ?: -1
        val countKind = runCatching { explanationDao.countByKind(kind) }.getOrNull() ?: -1
        android.util.Log.d(
            "Explanation",
            "Looking up word='$word' kind='$kind' (db total=$countAll, db kind=$countKind)",
        )

        // 1. Pre-gen lookup
        val preGen = runCatching { explanationDao.get(word, kind) }.getOrNull()
        android.util.Log.d(
            "Explanation",
            "Pre-gen lookup result: " +
                if (preGen == null) "NULL" else "found word='${preGen.word}' subtype='${preGen.subtype}' explanation_len=${preGen.explanation?.length ?: 0}",
        )
        if (preGen != null && !preGen.explanation.isNullOrBlank()) {
            _state.value = ExplanationUi(
                isLoading = false,
                explanation = preGen.explanation,
                rule = preGen.rule.orEmpty(),
                examples = preGen.examples.orEmpty(),
                mnemonic = preGen.mnemonic.orEmpty(),
                source = "pre_gen",
            )
            return
        }

        // 2. Online (AskAi flow) — JSON-схема в запросе
        _state.value = _state.value.copy(isLoading = true, error = null)
        val settings = AiSettingsStore.snapshot(ctx)
        val keyStore = SecureKeyStore(ctx)
        val apiKey = keyStore.getKey(settings.activeProvider.name)
        if (apiKey.isNullOrBlank()) {
            _state.value = ExplanationUi(
                isLoading = false,
                error = "Pre-gen нет для этого слова. Подключи AI в Настройках для онлайн-объяснения.",
                errorIsAuth = true,
            )
            return
        }

        val provider = AiProviderRegistry.get(settings.activeProvider)
        val modelId = settings.modelFor(settings.activeProvider)

        val question = """
            Объясни кратко (для школьника 11 класса). Верни ТОЛЬКО валидный JSON-объект:
            {"explanation": "...", "rule": "...", "examples": "...", "mnemonic": "..."}
            Без markdown, без LaTeX, без обёртки ```. Дроби через /, корни через √, греческие буквы словами.
            Контекст: $fallbackContext
        """.trimIndent()
        val combinedContext = "Слово/выражение: $word. Тип тренажёра: $kind."

        // Cache lookup
        val cacheKey = sha256("${settings.activeProvider.name}|$modelId|EXPL|$word|$kind|${fallbackContext.trim()}")
        val cached = runCatching { aiCacheDao.get(cacheKey) }.getOrNull()
        if (cached != null) {
            parseAndApply(cached, "online_cached")
            return
        }

        if (!AiSettingsStore.canMakeRequest(ctx)) {
            _state.value = ExplanationUi(
                isLoading = false,
                error = "Достигнут дневной лимит (${settings.todayUsage}/${settings.dailyLimit}). " +
                    "Сбрось счётчик в Настройках → AI → Лимит.",
            )
            return
        }

        when (val resp = provider.ask(question, combinedContext, modelId, apiKey)) {
            is AiResponse.Success -> {
                AiSettingsStore.incrementTodayUsage(ctx)
                val cleaned = LatexCleaner.clean(resp.text)
                runCatching {
                    aiCacheDao.put(
                        AiResponseCacheEntity(
                            cacheKey = cacheKey,
                            response = cleaned,
                            cachedAt = System.currentTimeMillis(),
                        ),
                    )
                }
                parseAndApply(cleaned, "online_ai")
            }
            is AiResponse.Error -> {
                val message = if (resp.isRateLimit) {
                    "Лимит провайдера. Подожди пару минут или переключи провайдера в Настройках."
                } else resp.message
                _state.value = ExplanationUi(
                    isLoading = false,
                    error = message,
                    errorIsAuth = resp.isAuthError,
                )
            }
        }
    }

    private fun parseAndApply(text: String, source: String) {
        val trimmed = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val obj = runCatching { JSONObject(trimmed) }.getOrNull()
        _state.value = if (obj != null) {
            ExplanationUi(
                isLoading = false,
                explanation = obj.optString("explanation"),
                rule = obj.optString("rule"),
                examples = obj.optString("examples"),
                mnemonic = obj.optString("mnemonic"),
                source = source,
            )
        } else {
            ExplanationUi(
                isLoading = false,
                explanation = text,
                source = "${source}_raw",
            )
        }
    }
}

private fun sha256(input: String): String {
    val md = MessageDigest.getInstance("SHA-256")
    val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplanationBottomSheet(
    word: String,
    kind: String,
    fallbackContext: String,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    vm: ExplanationViewModel = viewModel(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val st by vm.state.collectAsState()
    var activeTab by remember { mutableStateOf(ExplanationTab.WHY) }

    LaunchedEffect(word, kind) { vm.load(word, kind, fallbackContext) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = BgElevated,
        dragHandle = { CapsuleDragHandle() },
        // Параметр `windowInsets` в нашей версии Material3 (Compose BOM
        // 2024.12.01) не выставляется здесь. Управляем insets через
        // `windowInsetsPadding(WindowInsets.navigationBars)` на Column.
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            // Header: 📖 + word + inline badge | ✕
            SheetHeader(
                word = word,
                source = st.source,
                onClose = onDismiss,
            )
            Spacer(Modifier.height(14.dp))

            // Pill-style tabs
            PillTabRow(
                tabs = ExplanationTab.entries,
                activeTab = activeTab,
                onSelect = { activeTab = it },
            )
            Spacer(Modifier.height(16.dp))

            // Content area с адаптивной высотой
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp, max = 400.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                when {
                    st.isLoading -> LoadingState()
                    st.error != null -> ErrorState(
                        error = st.error!!,
                        showSettingsCta = st.errorIsAuth,
                        onOpenSettings = {
                            onOpenSettings()
                        },
                    )
                    else -> {
                        val content = when (activeTab) {
                            ExplanationTab.WHY -> st.explanation
                            ExplanationTab.RULE -> st.rule
                            ExplanationTab.EXAMPLES -> st.examples
                            ExplanationTab.MNEMONIC -> st.mnemonic
                        }
                        if (content.isBlank()) {
                            EmptyTabContent(activeTab)
                        } else {
                            SimpleMarkdownRenderer(markdown = content)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CapsuleDragHandle() {
    // Кастомный drag handle — iOS-style капсула 40×4dp с верхним padding.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(LabelTertiary.copy(alpha = 0.55f)),
        )
    }
}

@Composable
private fun SheetHeader(
    word: String,
    source: String,
    onClose: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "📖",
            fontSize = 30.sp,
            modifier = Modifier.padding(end = 12.dp),
        )
        Column(modifier = Modifier
            .padding(end = 8.dp)
            .weight(1f, fill = true),
        ) {
            Text(
                text = word.ifBlank { "Объяснение" },
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = Label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (source.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                SourceBadge(source = source)
            }
        }
        // ✕ кнопка
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(BgElevated2)
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClose()
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "✕",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = LabelSecondary,
            )
        }
    }
}

@Composable
private fun SourceBadge(source: String) {
    val (icon, label, color) = when (source) {
        "pre_gen" -> Triple("✨", "Opus 4.7", SystemBlue)
        "online_cached" -> Triple("💾", "Кеш AI", SystemBlue)
        "online_ai" -> Triple("🌐", "Онлайн AI", SystemOrange)
        "online_ai_raw" -> Triple("🌐", "Онлайн (raw)", SystemOrange)
        "online_cached_raw" -> Triple("💾", "Кеш (raw)", SystemBlue)
        else -> Triple("📖", "Объяснение", LabelSecondary)
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = icon,
            fontSize = 11.sp,
            modifier = Modifier.padding(end = 4.dp),
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun PillTabRow(
    tabs: List<ExplanationTab>,
    activeTab: ExplanationTab,
    onSelect: (ExplanationTab) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgElevated2)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        tabs.forEach { tab ->
            val isActive = tab == activeTab
            val bg by animateColorAsState(
                targetValue = if (isActive) BgElevated else Color.Transparent,
                animationSpec = tween(durationMillis = 200),
                label = "tab_bg",
            )
            val fg by animateColorAsState(
                targetValue = if (isActive) Label else LabelSecondary,
                animationSpec = tween(durationMillis = 200),
                label = "tab_fg",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(bg)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSelect(tab)
                    }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = tab.shortTitle,
                    fontSize = 13.sp,
                    color = fg,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = SystemBlue,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Загружаем объяснение…",
                fontSize = 13.sp,
                color = LabelSecondary,
            )
        }
    }
}

@Composable
private fun ErrorState(
    error: String,
    showSettingsCta: Boolean,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("⚠️", fontSize = 36.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            text = error,
            fontSize = 14.sp,
            color = SystemRed,
            textAlign = TextAlign.Center,
        )
        if (showSettingsCta) {
            Spacer(Modifier.height(16.dp))
            PrimaryButton(
                text = "Открыть Настройки → AI",
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun EmptyTabContent(tab: ExplanationTab) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("📭", fontSize = 40.sp)
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Раздел «${tab.fullTitle.lowercase()}» для этого слова пуст",
                fontSize = 13.sp,
                color = LabelSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}
