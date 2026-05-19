package com.daniel.ege100.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.daniel.ege100.ai.AiProviderType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * Phase 4 Stage A4 — настройки AI-помощника (Convention #41).
 *
 * Хранится в отдельном DataStore «ai_settings» (не пересекается с
 * app_settings — отдельная фича). API ключи **не здесь**, они в
 * SecureKeyStore (Convention #39).
 *
 * Дефолт — OpenRouter + openrouter/free (auto-router бесплатных моделей,
 * Convention #46). Пользователь получает бесплатный AI сразу после ввода
 * ключа OpenRouter. OpenRouter ротирует бесплатные модели в каталоге —
 * auto-router сам выбирает доступную, не привязывает нас к конкретной.
 *
 * todayUsage сбрасывается ежедневно: при чтении settings.todayUsage
 * сравниваем сохранённую дату с today; если не совпадает — возвращаем 0
 * (старая дата автоматически забывается при следующем incrementTodayUsage).
 */
@Serializable
data class AiSettings(
    val activeProvider: AiProviderType = AiProviderType.OPENROUTER,
    val modelByProvider: Map<String, String> = defaultModels(),
    val dailyLimit: Int = 200,
    val todayUsage: Int = 0,
    val todayDate: String = "",
) {
    fun modelFor(provider: AiProviderType): String =
        modelByProvider[provider.name] ?: when (provider) {
            AiProviderType.OPENROUTER -> "openrouter/free"
            AiProviderType.GEMINI -> "gemini-2.0-flash"
            AiProviderType.ANTHROPIC -> "claude-sonnet-4-6"
        }

    companion object {
        fun defaultModels(): Map<String, String> = mapOf(
            AiProviderType.OPENROUTER.name to "openrouter/free",
            AiProviderType.GEMINI.name to "gemini-2.0-flash",
            AiProviderType.ANTHROPIC.name to "claude-sonnet-4-6",
        )
    }
}

private val Context.aiSettingsStore by preferencesDataStore("ai_settings")

object AiSettingsStore {
    private val ACTIVE_PROVIDER = stringPreferencesKey("active_provider")
    private val MODEL_OPENROUTER = stringPreferencesKey("model_openrouter")
    private val MODEL_GEMINI = stringPreferencesKey("model_gemini")
    private val MODEL_ANTHROPIC = stringPreferencesKey("model_anthropic")
    private val DAILY_LIMIT = intPreferencesKey("daily_limit")
    private val TODAY_USAGE = intPreferencesKey("today_usage")
    private val TODAY_DATE = stringPreferencesKey("today_date")

    fun settingsFlow(context: Context): Flow<AiSettings> =
        context.aiSettingsStore.data.map { prefs ->
            val today = LocalDate.now().toString()
            val savedDate = prefs[TODAY_DATE] ?: ""
            val usage = if (savedDate == today) prefs[TODAY_USAGE] ?: 0 else 0
            AiSettings(
                activeProvider = runCatching {
                    AiProviderType.valueOf(prefs[ACTIVE_PROVIDER] ?: "OPENROUTER")
                }.getOrDefault(AiProviderType.OPENROUTER),
                modelByProvider = mapOf(
                    AiProviderType.OPENROUTER.name to (prefs[MODEL_OPENROUTER] ?: "openrouter/free"),
                    AiProviderType.GEMINI.name to (prefs[MODEL_GEMINI] ?: "gemini-2.0-flash"),
                    AiProviderType.ANTHROPIC.name to (prefs[MODEL_ANTHROPIC] ?: "claude-sonnet-4-6"),
                ),
                // Phase 4 Stage P4-C2 part В.3 (Convention #59) — default 50→200.
                // 50 было слишком мало для активной подготовки.
                dailyLimit = prefs[DAILY_LIMIT] ?: 200,
                todayUsage = usage,
                todayDate = if (savedDate == today) today else "",
            )
        }

    suspend fun snapshot(context: Context): AiSettings = settingsFlow(context).first()

    suspend fun setActiveProvider(context: Context, type: AiProviderType) {
        context.aiSettingsStore.edit { it[ACTIVE_PROVIDER] = type.name }
    }

    suspend fun setModelFor(context: Context, type: AiProviderType, modelId: String) {
        val key = when (type) {
            AiProviderType.OPENROUTER -> MODEL_OPENROUTER
            AiProviderType.GEMINI -> MODEL_GEMINI
            AiProviderType.ANTHROPIC -> MODEL_ANTHROPIC
        }
        context.aiSettingsStore.edit { it[key] = modelId }
    }

    suspend fun setDailyLimit(context: Context, limit: Int) {
        context.aiSettingsStore.edit { it[DAILY_LIMIT] = limit.coerceIn(1, 1000) }
    }

    suspend fun incrementTodayUsage(context: Context) {
        val today = LocalDate.now().toString()
        context.aiSettingsStore.edit { prefs ->
            val savedDate = prefs[TODAY_DATE]
            val current = if (savedDate == today) prefs[TODAY_USAGE] ?: 0 else 0
            prefs[TODAY_USAGE] = current + 1
            prefs[TODAY_DATE] = today
        }
    }

    /**
     * Phase 4 Stage P4-C2 part В.2 (Convention #59) — ручной сброс счётчика.
     * Полезно если пользователь случайно сжёг лимит на тестовых запросах
     * или хочет проверить новую модель не дожидаясь следующего дня. Дата
     * сохраняется — следующее `incrementTodayUsage` пойдёт с 0+1.
     */
    suspend fun resetTodayUsage(context: Context) {
        val today = LocalDate.now().toString()
        context.aiSettingsStore.edit { prefs ->
            prefs[TODAY_USAGE] = 0
            prefs[TODAY_DATE] = today
        }
    }

    /** Phase 4 Stage A5 — проверка лимита перед запросом. */
    suspend fun canMakeRequest(context: Context): Boolean {
        val s = snapshot(context)
        return s.todayUsage < s.dailyLimit
    }

    suspend fun restore(context: Context, settings: AiSettings) {
        context.aiSettingsStore.edit { prefs ->
            prefs[ACTIVE_PROVIDER] = settings.activeProvider.name
            settings.modelByProvider[AiProviderType.OPENROUTER.name]?.let { prefs[MODEL_OPENROUTER] = it }
            settings.modelByProvider[AiProviderType.GEMINI.name]?.let { prefs[MODEL_GEMINI] = it }
            settings.modelByProvider[AiProviderType.ANTHROPIC.name]?.let { prefs[MODEL_ANTHROPIC] = it }
            prefs[DAILY_LIMIT] = settings.dailyLimit
            // today_usage НЕ восстанавливаем — оно привязано к текущему дню,
            // импорт старого бэкапа не должен сбрасывать счётчик дня.
        }
    }

    suspend fun clearAll(context: Context) {
        context.aiSettingsStore.edit { it.clear() }
    }
}
