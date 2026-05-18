package com.daniel.ege100.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Stage 5 part Г — справочник правил.
 *
 * Источник: `assets/rules.json` (~140 KB, 46 правил, генерируется
 * `parser/scrapers/generate_rules.py`). Кешируется в памяти. Поиск по ключу
 * `${subject}_${typeNumber}`, например `math_6` или `rus_9`.
 *
 * Маппинг subject: subjects.slug в БД — "mathb" / "rus". В rules.json subject
 * "math" / "rus" — поэтому при поиске для математики делаем перевод.
 */
@Serializable
data class RuleEntry(
    val subject: String,
    val type_number: Int,
    val title: String,
    val markdown: String,
)

@Serializable
data class RulesDict(
    val version: String,
    val rules: Map<String, RuleEntry>,
)

object RulesRepository {
    private const val ASSET_NAME = "rules.json"
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile private var cached: RulesDict? = null

    private suspend fun load(context: Context): RulesDict = withContext(Dispatchers.IO) {
        cached?.let { return@withContext it }
        synchronized(this) {
            cached?.let { return@withContext it }
            val text = context.assets.open(ASSET_NAME).use {
                it.bufferedReader(Charsets.UTF_8).readText()
            }
            val parsed = json.decodeFromString(RulesDict.serializer(), text)
            cached = parsed
            parsed
        }
    }

    /**
     * @param subjectSlug "mathb" или "rus" (значение в БД).
     * @param typeNumber  номер типа задачи (1..27).
     */
    suspend fun getRule(context: Context, subjectSlug: String, typeNumber: Int): RuleEntry? {
        val dict = load(context)
        val key = when (subjectSlug) {
            "mathb" -> "math_$typeNumber"
            "rus" -> "rus_$typeNumber"
            else -> return null
        }
        return dict.rules[key]
    }
}
