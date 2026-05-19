package com.daniel.ege100.ui.ai

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.ImeAction
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
import com.daniel.ege100.data.SecureKeyStore
import com.daniel.ege100.data.UserDataDatabase
import com.daniel.ege100.ui.common.AppleCard
import com.daniel.ege100.ui.common.IosTextField
import com.daniel.ege100.ui.common.PrimaryButton
import com.daniel.ege100.ui.common.SecondaryButton
import com.daniel.ege100.ui.common.SimpleMarkdownRenderer
import com.daniel.ege100.ui.theme.BgElevated
import com.daniel.ege100.ui.theme.BgElevated2
import com.daniel.ege100.ui.theme.Label
import com.daniel.ege100.ui.theme.LabelSecondary
import com.daniel.ege100.ui.theme.LabelTertiary
import com.daniel.ege100.ui.theme.SystemBlue
import com.daniel.ege100.ui.theme.SystemBlueTint
import com.daniel.ege100.ui.theme.SystemRed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.MessageDigest

/**
 * Phase 4 Stage A5 — окно «Спросить ИИ» (Convention #41).
 *
 * Логика:
 *   1. Считаем sha256 кеш-ключа (provider + model + question + context).
 *   2. Если в кеше есть — отдаём моментально (без сети, без incrementUsage).
 *   3. Иначе проверяем daily limit. Если перебор — error.
 *   4. Зовём provider.ask(...) с ключом из SecureKeyStore.
 *   5. Success → incrementTodayUsage + кешируем + показываем.
 *   6. Error → показываем сообщение + флаги (isAuthError → ссылка на Настройки).
 */
data class AskAiUi(
    val isLoading: Boolean = false,
    val response: String? = null,
    val error: String? = null,
    val errorIsAuth: Boolean = false,
    val errorIsRateLimit: Boolean = false,
    val providerName: String = "",
    val modelName: String = "",
    val cached: Boolean = false,
)

class AskAiViewModel(app: Application) : AndroidViewModel(app) {
    private val cacheDao = UserDataDatabase.get(app).aiResponseCacheDao()
    private val _state = MutableStateFlow(AskAiUi())
    val state: StateFlow<AskAiUi> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val s = AiSettingsStore.snapshot(getApplication())
            val provider = AiProviderRegistry.get(s.activeProvider)
            val modelId = s.modelFor(s.activeProvider)
            val model = provider.availableModels.firstOrNull { it.id == modelId }
            _state.value = _state.value.copy(
                providerName = provider.displayName,
                modelName = model?.displayName ?: modelId,
            )
        }
    }

    fun reset() {
        _state.value = _state.value.copy(
            isLoading = false,
            response = null,
            error = null,
            errorIsAuth = false,
            errorIsRateLimit = false,
            cached = false,
        )
    }

    fun ask(question: String, context: String) {
        if (_state.value.isLoading) return
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            val settings = AiSettingsStore.snapshot(ctx)
            val provider = AiProviderRegistry.get(settings.activeProvider)
            val modelId = settings.modelFor(settings.activeProvider)

            // Quick fix #3 — диагностика передачи контекста (можно убрать после релиза).
            android.util.Log.d("AskAi", "Question: $question")
            android.util.Log.d(
                "AskAi",
                "Context length: ${context.length}, preview: ${context.take(200)}",
            )
            android.util.Log.d("AskAi", "Provider: ${provider.type}, model: $modelId")

            // Quick fix #3 — защита от пустого условия (Convention #47).
            // Если stripHtmlForAi вернул "" — у AI нет шансов ответить осмысленно.
            if (context.isBlank()) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    response = null,
                    error = "Не удалось извлечь условие задачи. Скриншот сделай отдельно.",
                )
                return@launch
            }

            val keyStore = SecureKeyStore(ctx)
            val apiKey = keyStore.getKey(settings.activeProvider.name)
            if (apiKey.isNullOrBlank()) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    response = null,
                    error = "API ключ не задан. Открой Настройки → AI помощник.",
                    errorIsAuth = true,
                )
                return@launch
            }

            val cacheKey = sha256("${settings.activeProvider.name}|$modelId|${question.trim()}|${context.trim()}")
            val cached = cacheDao.get(cacheKey)
            if (cached != null) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    response = cached,
                    error = null,
                    errorIsAuth = false,
                    errorIsRateLimit = false,
                    cached = true,
                )
                return@launch
            }

            if (!AiSettingsStore.canMakeRequest(ctx)) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Достигнут дневной лимит (${settings.dailyLimit}). " +
                        "Завтра сбросится автоматически или измени лимит в Настройках.",
                )
                return@launch
            }

            _state.value = _state.value.copy(isLoading = true, error = null, response = null, cached = false)

            when (val resp = provider.ask(question, context, modelId, apiKey)) {
                is AiResponse.Success -> {
                    AiSettingsStore.incrementTodayUsage(ctx)
                    // Phase 4 Stage P4-C part Б (Convention #49):
                    // Очищаем LaTeX ДО кеширования — в кеш сохраняется
                    // готовый-к-рендеру текст, повторный запрос не платит
                    // ни за токены, ни за повторную чистку.
                    val cleaned = LatexCleaner.clean(resp.text)
                    cacheDao.put(
                        AiResponseCacheEntity(
                            cacheKey = cacheKey,
                            response = cleaned,
                            cachedAt = System.currentTimeMillis(),
                        ),
                    )
                    _state.value = _state.value.copy(
                        isLoading = false,
                        response = cleaned,
                        error = null,
                    )
                }
                is AiResponse.Error -> {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = resp.message,
                        errorIsAuth = resp.isAuthError,
                        errorIsRateLimit = resp.isRateLimit,
                        response = null,
                    )
                }
            }
        }
    }
}

private fun sha256(input: String): String {
    val md = MessageDigest.getInstance("SHA-256")
    val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
}

/**
 * Phase 4 Stage P4-C part Д (Convention #53) — кастомные quick questions
 * для AI в тренажёрах. Каждый чип = пара (label, full-question).
 * Если null — используем дефолтный набор для задач (Объясни/Где ошибка/
 * Формула/Похожий).
 */
data class QuickQuestion(val label: String, val question: String)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AskAiBottomSheet(
    problemContext: String,
    userAnswerForHint: String?,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    customQuickQuestions: List<QuickQuestion>? = null,
    vm: AskAiViewModel = viewModel(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val st by vm.state.collectAsState()
    var question by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.reset() }

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
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // Шапка
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🤖", fontSize = 28.sp)
                Spacer(Modifier.size(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Спросить ИИ", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Label)
                    Text(
                        "${st.providerName} · ${st.modelName}",
                        fontSize = 12.sp,
                        color = LabelSecondary,
                    )
                }
                if (st.cached) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(SystemBlueTint)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text("из кеша", fontSize = 10.sp, color = SystemBlue, fontWeight = FontWeight.Medium)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // Быстрые вопросы
            Text("Быстрые вопросы:", fontSize = 12.sp, color = LabelSecondary, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            val errorHint = if (!userAnswerForHint.isNullOrBlank()) {
                "Я ответил «$userAnswerForHint». Почему это неверно?"
            } else "Покажи где у меня могла быть ошибка."
            val quickQuestions = customQuickQuestions ?: listOf(
                QuickQuestion("Объясни решение", "Объясни решение этой задачи по шагам."),
                QuickQuestion("Где ошибка?", errorHint),
                QuickQuestion("Какая формула?", "Какая формула используется в этой задаче? Объясни как ей пользоваться."),
                QuickQuestion("Похожий пример", "Приведи похожий пример и реши его, чтобы я понял подход."),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                quickQuestions.forEach { q ->
                    QuickQuestionChip(q.label) { question = q.question }
                }
            }
            Spacer(Modifier.height(16.dp))

            IosTextField(
                value = question,
                onValueChange = { question = it },
                placeholder = "Свой вопрос…",
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
            )
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                text = if (st.isLoading) "Думаю…" else "Спросить",
                onClick = { vm.ask(question.trim(), problemContext) },
                enabled = question.isNotBlank() && !st.isLoading,
            )

            Spacer(Modifier.height(16.dp))

            // Результат
            when {
                st.isLoading -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                    ) {
                        CircularProgressIndicator(color = SystemBlue)
                    }
                }
                st.error != null -> {
                    AppleCard(paddingDp = 16, background = BgElevated2) {
                        Column {
                            Text(
                                "❌ ${st.error}",
                                fontSize = 14.sp,
                                color = SystemRed,
                                lineHeight = 20.sp,
                            )
                            if (st.errorIsAuth || st.errorIsRateLimit) {
                                Spacer(Modifier.height(10.dp))
                                SecondaryButton(
                                    text = "Открыть настройки AI",
                                    onClick = onOpenSettings,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
                st.response != null -> {
                    AppleCard(paddingDp = 18) {
                        SimpleMarkdownRenderer(st.response!!)
                    }
                }
                else -> {
                    Text(
                        "Задай вопрос — ИИ объяснит задачу или подскажет где ошибка. " +
                            "Не списывай — пробуй сам, потом сверяйся.",
                        fontSize = 13.sp,
                        color = LabelTertiary,
                        lineHeight = 18.sp,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun QuickQuestionChip(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(SystemBlueTint)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(text, fontSize = 13.sp, color = SystemBlue, fontWeight = FontWeight.Medium)
    }
}
