package com.daniel.ege100.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Phase 4 Stage P4-D (Convention #72) — три новых русских тренажёра, источник
 * `corpus.db` через парсеры `extract_paronyms.py`, `extract_pleonasms.py`,
 * `extract_grammar_errors.py`. JSON-файлы в assets.
 */

@Serializable
data class ParonymItem(
    val problem_id: Long? = null,
    val sentence: String,
    val wrong_word: String,
    val correct_word: String,
)

@Serializable
data class PleonasmItem(
    val problem_id: Long? = null,
    val sentence: String,
    val extra_word: String,
)

@Serializable
data class GrammarOption(
    val text: String,
    val is_correct: Boolean,
)

/**
 * Phase 4 Stage P4-D4 (Convention #79) — формат multi-choice.
 *
 * Старый формат P4-D (`sentence` + `error_word` + `correct_form`) был тап-на-слово
 * в составной фразе с 5 пунктами через ` · ` — неудобно на устройстве. Новый
 * формат: одно `wrong_sentence` с CAPS-маркером + 4 `options` (1 правильный +
 * 3 правдоподобных дистрактора). `error_type` показывается в заголовке.
 */
@Serializable
data class GrammarErrorItem(
    val id: String = "",
    val problem_id: Long? = null,
    val error_type: String = "",
    val wrong_sentence: String = "",
    val options: List<GrammarOption> = emptyList(),
)

object ParonymsRepository {
    private const val ASSET = "paronyms.json"
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile private var cached: List<ParonymItem>? = null

    suspend fun load(context: Context): List<ParonymItem> = withContext(Dispatchers.IO) {
        cached?.let { return@withContext it }
        synchronized(this) {
            cached?.let { return@withContext it }
            val text = context.assets.open(ASSET).use { it.bufferedReader(Charsets.UTF_8).readText() }
            val parsed = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(ParonymItem.serializer()), text)
            cached = parsed
            parsed
        }
    }
}

object PleonasmsRepository {
    private const val ASSET = "pleonasms.json"
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile private var cached: List<PleonasmItem>? = null

    suspend fun load(context: Context): List<PleonasmItem> = withContext(Dispatchers.IO) {
        cached?.let { return@withContext it }
        synchronized(this) {
            cached?.let { return@withContext it }
            val text = context.assets.open(ASSET).use { it.bufferedReader(Charsets.UTF_8).readText() }
            val parsed = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(PleonasmItem.serializer()), text)
            cached = parsed
            parsed
        }
    }
}

object GrammarErrorsRepository {
    private const val ASSET = "grammar_errors.json"
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile private var cached: List<GrammarErrorItem>? = null

    suspend fun load(context: Context): List<GrammarErrorItem> = withContext(Dispatchers.IO) {
        cached?.let { return@withContext it }
        synchronized(this) {
            cached?.let { return@withContext it }
            val text = context.assets.open(ASSET).use { it.bufferedReader(Charsets.UTF_8).readText() }
            val parsed = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(GrammarErrorItem.serializer()), text)
            cached = parsed
            parsed
        }
    }
}

/**
 * Phase 4 Stage P4-D5 (Convention #83) — тренажёр словосочетаний для №7 ЕГЭ.
 *
 * Двухшаговая логика: пользователь сначала выбирает ошибочное словосочетание
 * из 5, затем вводит правильную форму в OutlinedTextField. Принимаются все
 * варианты из `correctAnswers` (Convention #85 — tolerant input checking).
 *
 * Источник — `parser/scrapers/extract_collocations.py` из corpus.db (задачи №7
 * русского). 269 валидных задач (минимум 30 по spec'у).
 */
@kotlinx.serialization.Serializable
data class CollocationItem(
    val id: String = "",
    val problem_id: Long? = null,
    val items: List<String> = emptyList(),
    val wrong_index: Int = 0,
    val wrong_word_in_phrase: String = "",
    val correct_answers: List<String> = emptyList(),
)

object CollocationsRepository {
    private const val ASSET = "word_collocations.json"
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile private var cached: List<CollocationItem>? = null

    suspend fun load(context: Context): List<CollocationItem> = withContext(Dispatchers.IO) {
        cached?.let { return@withContext it }
        synchronized(this) {
            cached?.let { return@withContext it }
            val text = context.assets.open(ASSET).use { it.bufferedReader(Charsets.UTF_8).readText() }
            val parsed = json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(CollocationItem.serializer()),
                text,
            )
            cached = parsed
            parsed
        }
    }
}
