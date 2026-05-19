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
 *
 * Stage P4-C part Б (Convention #49) — жёсткий запрет LaTeX. Раньше
 * модели присылали `\(\frac{4}{7}\)`, `\cdot`, `\sqrt{x}` —
 * SimpleMarkdownRenderer не умеет это рендерить, пользователь видел
 * «техно-кашу» из обратных слешей и фигурных скобок. Двойная защита:
 *   (1) этот промпт явно запрещает LaTeX с примерами «как надо»,
 *   (2) cleanLatex() в AskAiViewModel чистит то что модель всё-таки прислала.
 */
val EGE100_SYSTEM_PROMPT: String = """
Ты помогаешь школьнику разобраться с задачами ЕГЭ по математике или русскому.

В сообщении пользователя после слова «Задача:» идёт реальный текст условия с экрана приложения. Формулы расшифрованы словами в скобках («дробь: числитель …, знаменатель …», «корень из …» и т.п.) — это нормально, опирайся на них. После слова «Вопрос:» идёт вопрос ученика.

КРИТИЧНО — НИКОГДА не используй LaTeX, MathML или специальную математическую разметку. Это твоё САМОЕ ВАЖНОЕ правило. Приложение НЕ рендерит \( \) \[ \] \frac \sqrt и т.п. — пользователь увидит сырые слеши и фигурные скобки.

ПРАВИЛА ФОРМУЛ:
- Дроби пиши через слэш: 4/7 (не \frac{4}{7}).
- Смешанные дроби словами или через дефис: 7 целых 3/7 (не 7\frac{3}{7}).
- Умножение точкой или звёздочкой: 4 · x или 4*x (не \cdot).
- Степени через ^: x^2 (не x^{2}).
- Корни словами: корень из 5 или √5 (не \sqrt{5}).
- Греческие буквы юникод-символами: α, β, π, σ (не \alpha, \beta, \pi).
- Сравнения юникод-символами: ≤ ≥ ≠ ≈ → ∞ (не \leq, \geq, \neq, \approx, \to, \infty).

Запрещённые символы: \ $ { } ^{ _{ — не пиши их вообще.

ПРИМЕР ХОРОШО:
Решим уравнение 4/7 · x = 7 целых 3/7.
Шаг 1: 7 целых 3/7 = 52/7.
Шаг 2: 4/7 · x = 52/7. Умножим обе части на 7: 4x = 52.
Шаг 3: x = 13.
Ответ: 13.

Объясняй просто, по шагам. Допустим лёгкий markdown (заголовки ##, списки -, **жирный**). Максимум 350 слов.
""".trimIndent()
