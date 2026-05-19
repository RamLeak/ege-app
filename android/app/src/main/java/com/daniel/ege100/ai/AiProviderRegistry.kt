package com.daniel.ege100.ai

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Phase 4 Stage A3 — singleton registry для AI-провайдеров.
 *
 * Создаётся один OkHttpClient с разумными таймаутами (30 секунд) и
 * переиспользуется тремя провайдерами. AI-запросы могут идти долго
 * (Sonnet/Opus с большим контекстом), но > 30 секунд скорее всего
 * сетевая проблема — лучше отвалиться с error message.
 */
object AiProviderRegistry {
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    val openRouter: AiProvider by lazy { OpenRouterProvider(httpClient) }
    val gemini: AiProvider by lazy { GeminiProvider(httpClient) }
    val anthropic: AiProvider by lazy { AnthropicProvider(httpClient) }

    val all: Map<AiProviderType, AiProvider> by lazy {
        mapOf(
            AiProviderType.OPENROUTER to openRouter,
            AiProviderType.GEMINI to gemini,
            AiProviderType.ANTHROPIC to anthropic,
        )
    }

    fun get(type: AiProviderType): AiProvider = all[type]
        ?: throw IllegalStateException("Unknown AI provider $type")
}
