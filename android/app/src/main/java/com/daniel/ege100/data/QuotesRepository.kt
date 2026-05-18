package com.daniel.ege100.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

/**
 * Phase 3 Stage B part А — справочник цитат для главного экрана.
 *
 * Источник: `assets/quotes.json` (~50KB, 150+ цитат, генерится
 * `parser/scrapers/generate_quotes.py`). Кешируется в памяти.
 *
 * Цитата дня детерминированна: индекс = `today.toEpochDay() % size`. Это
 * даёт одну цитату в день для всех пользователей + циклически возвращается
 * к началу примерно каждые 150 дней.
 */
@Serializable
data class Quote(
    val text: String,
    val author: String,
    val category: String = "general",
)

@Serializable
data class QuotesDict(
    val version: String,
    val quotes: List<Quote>,
)

object QuotesRepository {
    private const val ASSET_NAME = "quotes.json"
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile private var cached: List<Quote>? = null

    private suspend fun loadAll(context: Context): List<Quote> = withContext(Dispatchers.IO) {
        cached?.let { return@withContext it }
        synchronized(this) {
            cached?.let { return@withContext it }
            val raw = context.assets.open(ASSET_NAME).use {
                it.bufferedReader(Charsets.UTF_8).readText()
            }
            val parsed = json.decodeFromString(QuotesDict.serializer(), raw)
            cached = parsed.quotes
            parsed.quotes
        }
    }

    suspend fun getTodayQuote(context: Context, today: LocalDate = LocalDate.now()): Quote {
        val all = loadAll(context)
        if (all.isEmpty()) return Quote("Учись каждый день.", "EGE100", "general")
        val idx = (today.toEpochDay().mod(all.size.toLong())).toInt()
        return all[idx]
    }

    suspend fun size(context: Context): Int = loadAll(context).size
}
