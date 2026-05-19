package com.daniel.ege100.ai

/**
 * Phase 4 Stage A3 — интерфейс AI-провайдера (Convention #38).
 *
 * Три провайдера: OpenRouter (дефолт), Google Gemini, Anthropic Claude.
 * Каждый имплементирует ask(question, context, modelId, apiKey).
 *
 * Конструкция вопроса в UI:
 *   - `context` — текст условия задачи (HTML обрезаем до plain text).
 *   - `question` — вопрос пользователя ("Объясни решение" / "Где ошибка?").
 *   - `apiKey` — из SecureKeyStore.
 *   - `modelId` — выбранная модель из availableModels.
 *
 * Обработка ошибок:
 *   - 401/403 → `isAuthError = true` (UI предлагает открыть Настройки).
 *   - 429 → `isRateLimit = true` (UI говорит «подожди»).
 *   - Сетевые ошибки → message без флагов.
 */
sealed interface AiResponse {
    data class Success(val text: String, val tokensUsed: Int) : AiResponse
    data class Error(
        val message: String,
        val isAuthError: Boolean = false,
        val isRateLimit: Boolean = false,
    ) : AiResponse
}

enum class AiProviderType { OPENROUTER, GEMINI, ANTHROPIC }

data class AiModel(
    val id: String,
    val displayName: String,
    val description: String,
    val costHint: String,
    val isFree: Boolean = false,
)

interface AiProvider {
    val type: AiProviderType
    val displayName: String
    val description: String
    val signupUrl: String
    val keyHint: String
    val availableModels: List<AiModel>
    val defaultModelId: String

    suspend fun ask(
        question: String,
        context: String,
        modelId: String,
        apiKey: String,
    ): AiResponse
}

/**
 * Системный промпт — общий для всех провайдеров (Convention #38).
 *
 * Quick fix #3 (Convention #47): явно говорим что «Задача:» — это
 * фактический текст с экрана, формулы расшифрованы alt-текстом
 * («дробь: ...», «корень из ...»). Раньше модели иногда отвечали наугад
 * не цепляясь за условие.
 */
const val EGE100_SYSTEM_PROMPT: String =
    "Ты помогаешь школьнику разобраться с задачами ЕГЭ по математике или русскому. " +
        "В сообщении пользователя после слова «Задача:» идёт реальный текст условия " +
        "с экрана приложения. Формулы расшифрованы словами в скобках " +
        "(«дробь: числитель …, знаменатель …», «корень из …» и т.п.) — это нормально, " +
        "опирайся на них. После слова «Вопрос:» идёт вопрос ученика. " +
        "Объясняй просто, по шагам. Если формула важна — выписывай её. " +
        "Допустим лёгкий markdown (заголовки ##, списки -). Максимум 350 слов."
