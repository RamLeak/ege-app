package com.daniel.ege100.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Phase 4 Stage A3 — реализации трёх провайдеров через OkHttp.
 *
 * Все три парсят JSON руками через org.json — не тянем дополнительные
 * сериализаторы. Запросы — POST с JSON body, ответ — OpenAI-совместимый
 * формат у OpenRouter, специфический у Gemini и Anthropic.
 */

private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

// ---------------------------------------------------------------------------
// OpenRouterProvider — дефолт (Convention #38)
// ---------------------------------------------------------------------------

class OpenRouterProvider(private val httpClient: OkHttpClient) : AiProvider {
    override val type = AiProviderType.OPENROUTER
    override val displayName = "OpenRouter"
    override val description = "30+ моделей через один ключ. Есть бесплатные."
    override val signupUrl = "https://openrouter.ai/keys"
    override val keyHint = "sk-or-v1-..."

    override val availableModels = listOf(
        AiModel(
            id = "openrouter/free",
            displayName = "OpenRouter Auto (free)",
            description = "Автовыбор лучшей доступной бесплатной модели",
            costHint = "Бесплатно",
            isFree = true,
        ),
        AiModel(
            id = "deepseek/deepseek-v4-flash:free",
            displayName = "DeepSeek V4 Flash (free)",
            description = "Reasoning, отлично для математики (новая модель)",
            costHint = "Бесплатно",
            isFree = true,
        ),
        AiModel(
            id = "deepseek/deepseek-r1:free",
            displayName = "DeepSeek R1 (free)",
            description = "Reasoning (может быть недоступна — fallback)",
            costHint = "Бесплатно",
            isFree = true,
        ),
        AiModel(
            id = "meta-llama/llama-3.3-70b-instruct:free",
            displayName = "Llama 3.3 70B (free)",
            description = "Сильная open-source модель",
            costHint = "Бесплатно",
            isFree = true,
        ),
        AiModel(
            id = "qwen/qwen-2.5-72b-instruct:free",
            displayName = "Qwen 2.5 72B (free)",
            description = "Хорошо на русском языке",
            costHint = "Бесплатно",
            isFree = true,
        ),
        AiModel(
            id = "google/gemini-2.0-flash-exp:free",
            displayName = "Gemini 2.0 Flash (free)",
            description = "Быстрая модель Google",
            costHint = "Бесплатно",
            isFree = true,
        ),
        AiModel(
            id = "anthropic/claude-sonnet-4.6",
            displayName = "Claude Sonnet 4.6 (платно)",
            description = "Премиум-качество",
            costHint = "≈ $0.015/запрос",
        ),
        AiModel(
            id = "openai/gpt-4o",
            displayName = "GPT-4o (платно)",
            description = "Премиум-качество OpenAI",
            costHint = "≈ $0.02/запрос",
        ),
    )
    override val defaultModelId = "openrouter/free"

    override suspend fun ask(
        question: String,
        context: String,
        modelId: String,
        apiKey: String,
    ): AiResponse = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("model", modelId)
            put("max_tokens", 1024)
            put(
                "messages",
                JSONArray().apply {
                    put(
                        JSONObject().apply {
                            put("role", "system")
                            put("content", EGE100_SYSTEM_PROMPT)
                        },
                    )
                    put(
                        JSONObject().apply {
                            put("role", "user")
                            put("content", "Задача:\n$context\n\nВопрос: $question")
                        },
                    )
                },
            )
        }
        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("HTTP-Referer", "https://github.com/RamLeak/ege-app")
            .header("X-Title", "EGE100")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()
        runRequest(httpClient, request) { json ->
            val content = json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
            val tokens = json.optJSONObject("usage")?.optInt("total_tokens") ?: 0
            AiResponse.Success(content, tokens)
        }
    }
}

// ---------------------------------------------------------------------------
// GeminiProvider — Google AI Studio (1500/день free)
// ---------------------------------------------------------------------------

class GeminiProvider(private val httpClient: OkHttpClient) : AiProvider {
    override val type = AiProviderType.GEMINI
    override val displayName = "Google Gemini"
    override val description = "1500 запросов/день бесплатно. Google AI Studio."
    override val signupUrl = "https://aistudio.google.com/app/apikey"
    override val keyHint = "AIza..."

    override val availableModels = listOf(
        AiModel(
            id = "gemini-2.0-flash",
            displayName = "Gemini 2.0 Flash",
            description = "Быстрая, 1500/день",
            costHint = "Бесплатно (1500/день)",
            isFree = true,
        ),
        AiModel(
            id = "gemini-2.0-flash-lite",
            displayName = "Gemini 2.0 Flash Lite",
            description = "Самая быстрая",
            costHint = "Бесплатно",
            isFree = true,
        ),
        AiModel(
            id = "gemini-1.5-pro",
            displayName = "Gemini 1.5 Pro",
            description = "Качественнее, лимит ниже",
            costHint = "Бесплатно (ограниченно)",
            isFree = true,
        ),
    )
    override val defaultModelId = "gemini-2.0-flash"

    override suspend fun ask(
        question: String,
        context: String,
        modelId: String,
        apiKey: String,
    ): AiResponse = withContext(Dispatchers.IO) {
        val text = buildString {
            append(EGE100_SYSTEM_PROMPT)
            append("\n\nЗадача:\n")
            append(context)
            append("\n\nВопрос: ")
            append(question)
        }
        val body = JSONObject().apply {
            put(
                "contents",
                JSONArray().apply {
                    put(
                        JSONObject().apply {
                            put("role", "user")
                            put(
                                "parts",
                                JSONArray().apply {
                                    put(JSONObject().apply { put("text", text) })
                                },
                            )
                        },
                    )
                },
            )
            put(
                "generationConfig",
                JSONObject().apply {
                    put("maxOutputTokens", 1024)
                    put("temperature", 0.7)
                },
            )
        }
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent?key=$apiKey")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()
        runRequest(httpClient, request) { json ->
            val content = json.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
            val tokens = json.optJSONObject("usageMetadata")?.optInt("totalTokenCount") ?: 0
            AiResponse.Success(content, tokens)
        }
    }
}

// ---------------------------------------------------------------------------
// AnthropicProvider — прямой доступ к Claude (платный)
// ---------------------------------------------------------------------------

class AnthropicProvider(private val httpClient: OkHttpClient) : AiProvider {
    override val type = AiProviderType.ANTHROPIC
    override val displayName = "Anthropic Claude"
    override val description = "Прямой доступ к Claude. Лучшее качество. Платно."
    override val signupUrl = "https://console.anthropic.com/settings/keys"
    override val keyHint = "sk-ant-api03-..."

    override val availableModels = listOf(
        AiModel(
            id = "claude-haiku-4-5",
            displayName = "Claude Haiku 4.5",
            description = "Быстрая, дешёвая",
            costHint = "≈ $0.001/запрос",
        ),
        AiModel(
            id = "claude-sonnet-4-6",
            displayName = "Claude Sonnet 4.6",
            description = "Сбалансированная",
            costHint = "≈ $0.015/запрос",
        ),
        AiModel(
            id = "claude-opus-4-7",
            displayName = "Claude Opus 4.7",
            description = "Максимум интеллекта",
            costHint = "≈ $0.075/запрос",
        ),
    )
    override val defaultModelId = "claude-sonnet-4-6"

    override suspend fun ask(
        question: String,
        context: String,
        modelId: String,
        apiKey: String,
    ): AiResponse = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("model", modelId)
            put("max_tokens", 1024)
            put("system", EGE100_SYSTEM_PROMPT)
            put(
                "messages",
                JSONArray().apply {
                    put(
                        JSONObject().apply {
                            put("role", "user")
                            put("content", "Задача:\n$context\n\nВопрос: $question")
                        },
                    )
                },
            )
        }
        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()
        runRequest(httpClient, request) { json ->
            val content = json.getJSONArray("content").getJSONObject(0).getString("text")
            val tokens = json.optJSONObject("usage")?.let {
                it.optInt("input_tokens") + it.optInt("output_tokens")
            } ?: 0
            AiResponse.Success(content, tokens)
        }
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private inline fun runRequest(
    httpClient: OkHttpClient,
    request: Request,
    parseSuccess: (JSONObject) -> AiResponse,
): AiResponse {
    return try {
        httpClient.newCall(request).execute().use { response ->
            when (response.code) {
                401, 403 -> AiResponse.Error("Неверный API ключ", isAuthError = true)
                404 -> AiResponse.Error(
                    "Эта модель сейчас недоступна. Попробуй другую в Настройках → Модель.",
                    isAuthError = false,
                )
                429 -> AiResponse.Error("Превышен лимит запросов. Подожди.", isRateLimit = true)
                in 200..299 -> {
                    val body = response.body?.string().orEmpty()
                    runCatching { parseSuccess(JSONObject(body)) }.getOrElse { e ->
                        AiResponse.Error("Не удалось разобрать ответ: ${e.message}")
                    }
                }
                else -> {
                    val snippet = response.body?.string()?.take(200).orEmpty()
                    AiResponse.Error("Ошибка ${response.code}: $snippet")
                }
            }
        }
    } catch (e: Throwable) {
        AiResponse.Error("Ошибка сети: ${e.message}")
    }
}
