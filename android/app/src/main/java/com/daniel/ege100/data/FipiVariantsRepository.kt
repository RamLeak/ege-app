package com.daniel.ege100.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Phase 4 Stage B1 — pre-bundled варианты КИМ ФИПИ.
 *
 * Источник: `assets/fipi_variants.json`, генерируется
 * `parser/scrapers/parse_fipi_variants.py` (берёт случайные задачи из
 * corpus.db по одной из каждого типа предмета). Фиксированный набор —
 * у всех пользователей одинаковые варианты.
 *
 * Формат:
 *   variants[].id           — math_2024_v1 / rus_2024_v1
 *   variants[].subject      — "math" | "rus"
 *   variants[].tasks[].position — порядок в варианте (1-based)
 *   variants[].tasks[].typeNumber — номер типа ЕГЭ
 *   variants[].tasks[].problemId — id из corpus.db
 */
@Serializable
data class FipiVariant(
    val id: String,
    val title: String,
    val subject: String,
    val year: Int,
    val version: Int,
    val taskCount: Int,
    val tasks: List<FipiTask>,
)

@Serializable
data class FipiTask(
    val position: Int,
    val typeNumber: Int? = null,
    val problemId: Long? = null,
)

@Serializable
data class FipiVariantsDict(
    val version: String,
    val variants: List<FipiVariant>,
)

object FipiVariantsRepository {
    private const val ASSET_NAME = "fipi_variants.json"
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile private var cached: List<FipiVariant>? = null

    suspend fun getAllVariants(context: Context): List<FipiVariant> = withContext(Dispatchers.IO) {
        cached?.let { return@withContext it }
        synchronized(this) {
            cached?.let { return@withContext it }
            val raw = runCatching {
                context.assets.open(ASSET_NAME).use { it.bufferedReader(Charsets.UTF_8).readText() }
            }.getOrNull() ?: run {
                cached = emptyList()
                return@withContext emptyList()
            }
            val parsed = runCatching {
                json.decodeFromString(FipiVariantsDict.serializer(), raw)
            }.getOrNull()
            val list = parsed?.variants.orEmpty()
            cached = list
            list
        }
    }

    suspend fun getVariant(context: Context, id: String): FipiVariant? =
        getAllVariants(context).firstOrNull { it.id == id }

    suspend fun getMathVariants(context: Context): List<FipiVariant> =
        getAllVariants(context).filter { it.subject == "math" }

    suspend fun getRusVariants(context: Context): List<FipiVariant> =
        getAllVariants(context).filter { it.subject == "rus" }
}
