package com.daniel.ege100.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Орфоэпический словник ФИПИ ЕГЭ 2023 — 230 слов в 6 категориях.
 * JSON в `assets/accent_words.json`, парсится один раз при старте Categories
 * экрана и кешируется в памяти на время процесса.
 */
@Serializable
data class AccentWord(
    val word: String,
    val stressed_index: Int,
)

@Serializable
data class AccentCategory(
    val id: String,
    val title: String,
    val words: List<AccentWord>,
)

@Serializable
data class AccentDictionary(
    val version: String,
    val source: String,
    val categories: List<AccentCategory>,
)

object AccentWordsRepository {
    private const val ASSET_NAME = "accent_words.json"
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile private var cached: AccentDictionary? = null

    suspend fun load(context: Context): AccentDictionary = withContext(Dispatchers.IO) {
        cached?.let { return@withContext it }
        synchronized(this) {
            cached?.let { return@withContext it }
            val text = context.assets.open(ASSET_NAME).use { it.bufferedReader(Charsets.UTF_8).readText() }
            val parsed = json.decodeFromString(AccentDictionary.serializer(), text)
            cached = parsed
            parsed
        }
    }

    suspend fun loadCategory(context: Context, categoryId: String?): List<AccentWord> {
        val dict = load(context)
        return if (categoryId == null) {
            dict.categories.flatMap { it.words }
        } else {
            dict.categories.firstOrNull { it.id == categoryId }?.words.orEmpty()
        }
    }

    suspend fun categoryTitle(context: Context, categoryId: String?): String {
        val dict = load(context)
        if (categoryId == null) return "Все слова"
        return dict.categories.firstOrNull { it.id == categoryId }?.title ?: "Тренажёр"
    }

    /**
     * Phase 5 Stage E2 — определить категорию слова (nouns/verbs/...).
     *
     * Нужен для SrsRepository.addCardOnMistake: subtype в SRS-карточке должен
     * совпадать с реальной категорией, чтобы карточки на одно и то же слово
     * не дублировались в зависимости от режима пользователя (категория vs «все»).
     *
     * Возвращает id первой категории, в которой встречается слово, либо null
     * если слово не найдено в словнике.
     */
    suspend fun categoryFor(context: Context, word: String): String? {
        val dict = load(context)
        val normalized = word.lowercase()
        return dict.categories.firstOrNull { cat ->
            cat.words.any { it.word.lowercase() == normalized }
        }?.id
    }
}
