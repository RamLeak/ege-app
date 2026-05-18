package com.daniel.ege100.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Stage 4: словарь слов с пропусками для тренажёров №9-12 русского.
 *
 * Источник — `assets/word_blanks.json`, собранный `extract_word_blanks.py`
 * из `corpus.db`. Формат каждого слова:
 *   masked    — «р..сти» (с двумя точками вместо пропущенной буквы/букв).
 *   answer    — «а» (одна-три русские буквы).
 *   full      — «расти» (полное слово для подсветки и подсказки).
 *   rule_hint — короткая подсказка-правило (общая для типа).
 */
@Serializable
data class WordBlank(
    val masked: String,
    val answer: String,
    val full: String,
    val rule_hint: String,
)

@Serializable
data class WordBlanksType(
    val title: String,
    val full_title: String,
    val words: List<WordBlank>,
)

@Serializable
data class WordBlanksDict(
    val version: String,
    val source: String,
    val types: Map<String, WordBlanksType>,
)

object WordBlanksRepository {
    private const val ASSET_NAME = "word_blanks.json"
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile private var cached: WordBlanksDict? = null

    suspend fun load(context: Context): WordBlanksDict = withContext(Dispatchers.IO) {
        cached?.let { return@withContext it }
        synchronized(this) {
            cached?.let { return@withContext it }
            val text = context.assets.open(ASSET_NAME).use {
                it.bufferedReader(Charsets.UTF_8).readText()
            }
            val parsed = json.decodeFromString(WordBlanksDict.serializer(), text)
            cached = parsed
            parsed
        }
    }

    suspend fun loadType(context: Context, typeNumber: Int): WordBlanksType? {
        val dict = load(context)
        return dict.types[typeNumber.toString()]
    }
}

/**
 * Журнал ошибок тренажёров №9-12. Один Set<String> на тип (key:
 * `wrong_words_t<N>`). Phase 3 будет читать; пока только пишем.
 */
private val Context.wordBlankStore by preferencesDataStore("word_blank_errors")

object WordBlankErrorsStore {
    private fun keyFor(typeNumber: Int) = stringSetPreferencesKey("wrong_words_t$typeNumber")

    fun errorsFlow(context: Context, typeNumber: Int): Flow<Set<String>> =
        context.wordBlankStore.data.map { it[keyFor(typeNumber)] ?: emptySet() }

    suspend fun recordError(context: Context, typeNumber: Int, masked: String) {
        context.wordBlankStore.edit { prefs ->
            val key = keyFor(typeNumber)
            val cur = prefs[key] ?: emptySet()
            prefs[key] = cur + masked
        }
    }
}
