package com.daniel.ege100.ui.ai

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
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
import com.daniel.ege100.ui.common.SecondaryButton
import com.daniel.ege100.ui.common.SimpleMarkdownRenderer
import com.daniel.ege100.ui.theme.BgElevated
import com.daniel.ege100.ui.theme.BgElevated2
import com.daniel.ege100.ui.theme.Label
import com.daniel.ege100.ui.theme.LabelSecondary
import com.daniel.ege100.ui.theme.LabelTertiary
import com.daniel.ege100.ui.theme.Separator
import com.daniel.ege100.ui.theme.SystemBlue
import com.daniel.ege100.ui.theme.SystemBlueTint
import com.daniel.ege100.ui.theme.SystemGreen
import com.daniel.ege100.ui.theme.SystemRed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Phase 4 Stage P4-D (Convention #71) — единый bottom sheet с 4 табами
 * (Почему / Правило / Примеры / Запомнить).
 *
 * Алгоритм load():
 *   1. Pre-gen из corpus.db (TrainerExplanationDao.get).
 *      Source = "pre_gen" → badge «Pre-generated».
 *   2. Online cache (тот же AI-кеш, что у AskAi).
 *      Source = "online_cached".
 *   3. Online AI запрос — JSON с 4 полями. При успехе кешируется.
 *      Source = "online_ai".
 *   4. Если нет API ключа или AI выключен — error message с CTA на Настройки.
 *
 * Используется в 8 новых тренажёрах (3 рус + 5 матем) Phase 4 Stage P4-D.
 * AskAiBottomSheet (Convention #38) остаётся для свободных вопросов в задачах.
 */
enum class ExplanationTab(val title: String) {
    WHY("Почему"),
    RULE("Правило"),
    EXAMPLES("Примеры"),
    MNEMONIC("Запомнить"),
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

        // Phase 4 Stage P4-D5 fix (Convention #86) — диагностика. Видна через
        // `adb logcat -s Explanation:D` или CrashRecoveryDialog → Поделиться.
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

        // Cache lookup (тот же sha256 механизм что в AskAi)
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
            // Не JSON — кладём весь текст в explanation, остальное пусто.
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val st by vm.state.collectAsState()
    var activeTab by remember { mutableStateOf(ExplanationTab.WHY) }

    LaunchedEffect(word, kind) { vm.load(word, kind, fallbackContext) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = BgElevated,
        modifier = Modifier.fillMaxHeight(0.92f),
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            // Шапка
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📖", fontSize = 28.sp)
                Spacer(Modifier.size(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = word.ifBlank { "Объяснение" },
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Label,
                    )
                    Text(
                        text = sourceLabel(st.source),
                        fontSize = 12.sp,
                        color = LabelSecondary,
                    )
                }
                if (st.source == "pre_gen") {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(SystemBlueTint)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text("Opus 4.7", fontSize = 10.sp, color = SystemBlue, fontWeight = FontWeight.Medium)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(BgElevated2)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                ExplanationTab.entries.forEach { tab ->
                    TabChip(
                        title = tab.title,
                        active = activeTab == tab,
                        onClick = { activeTab = tab },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            // Content
            when {
                st.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = SystemBlue)
                            Spacer(Modifier.height(12.dp))
                            Text("Загружаем объяснение…", color = LabelSecondary, fontSize = 13.sp)
                        }
                    }
                }
                st.error != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                    ) {
                        Text("⚠️ ${st.error}", color = SystemRed, fontSize = 15.sp)
                        if (st.errorIsAuth) {
                            Spacer(Modifier.height(16.dp))
                            PrimaryButton(
                                text = "Открыть Настройки → AI",
                                onClick = onOpenSettings,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        SecondaryButton(
                            text = "Закрыть",
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                else -> {
                    val content = when (activeTab) {
                        ExplanationTab.WHY -> st.explanation
                        ExplanationTab.RULE -> st.rule
                        ExplanationTab.EXAMPLES -> st.examples
                        ExplanationTab.MNEMONIC -> st.mnemonic
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(360.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        if (content.isBlank()) {
                            Text(
                                text = "Для этого слова раздел «${activeTab.title.lowercase()}» пуст.",
                                color = LabelTertiary,
                                fontSize = 14.sp,
                            )
                        } else {
                            SimpleMarkdownRenderer(markdown = content)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    SecondaryButton(
                        text = "Закрыть",
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun sourceLabel(source: String): String = when (source) {
    "pre_gen" -> "Pre-generated (Opus 4.7)"
    "online_ai" -> "Онлайн (AI)"
    "online_cached" -> "Из кеша AI"
    "online_ai_raw" -> "Онлайн (AI, без структуры)"
    "online_cached_raw" -> "Из кеша (без структуры)"
    "" -> "Загружаем…"
    else -> source
}

@Composable
private fun TabChip(
    title: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) BgElevated else BgElevated2)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            color = if (active) Label else LabelSecondary,
            fontSize = 13.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}
