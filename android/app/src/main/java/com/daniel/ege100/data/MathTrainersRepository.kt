package com.daniel.ege100.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Phase 4 Stage P4-D (Convention #74) — данные для 5 математических тренажёров.
 *
 * Все JSON в `assets/`:
 *   trig_values.json, short_multiplication_formulas.json,
 *   log_power_properties.json, derivatives.json, geometric_formulas.json
 *
 * Структуры жёсткие, поля разные — здесь декларируем по одной dataclass и одному
 * Repository на каждый тренажёр; cache в памяти через @Volatile.
 */

@Serializable
data class TrigValue(
    val angle_deg: Int,
    val angle_rad: String,
    val sin: String,
    val cos: String,
    val tan: String,
    val ctg: String,
)

@Serializable
data class ShortMultFormula(
    val id: String,
    val name: String,
    val left: String,
    val right: String,
)

@Serializable
data class LogPowerProperty(
    val id: String,
    val left: String,
    val right: String,
)

@Serializable
data class Derivative(
    val function: String,
    val derivative: String,
)

@Serializable
data class GeometricFormula(
    val id: String,
    val shape: String,
    val find: String,
    val formula: String,
)

private val jsonParser = Json { ignoreUnknownKeys = true }

private suspend inline fun <reified T> loadList(
    context: Context,
    asset: String,
    serializer: kotlinx.serialization.KSerializer<T>,
    crossinline cache: () -> List<T>?,
    crossinline setCache: (List<T>) -> Unit,
): List<T> = withContext(Dispatchers.IO) {
    cache()?.let { return@withContext it }
    val text = context.assets.open(asset).use { it.bufferedReader(Charsets.UTF_8).readText() }
    val parsed = jsonParser.decodeFromString(ListSerializer(serializer), text)
    setCache(parsed)
    parsed
}

object TrigValuesRepository {
    @Volatile private var cached: List<TrigValue>? = null
    suspend fun load(context: Context): List<TrigValue> =
        loadList(context, "trig_values.json", TrigValue.serializer(), { cached }, { cached = it })
}

object ShortMultRepository {
    @Volatile private var cached: List<ShortMultFormula>? = null
    suspend fun load(context: Context): List<ShortMultFormula> =
        loadList(context, "short_multiplication_formulas.json", ShortMultFormula.serializer(), { cached }, { cached = it })
}

object LogPowerRepository {
    @Volatile private var cached: List<LogPowerProperty>? = null
    suspend fun load(context: Context): List<LogPowerProperty> =
        loadList(context, "log_power_properties.json", LogPowerProperty.serializer(), { cached }, { cached = it })
}

object DerivativesRepository {
    @Volatile private var cached: List<Derivative>? = null
    suspend fun load(context: Context): List<Derivative> =
        loadList(context, "derivatives.json", Derivative.serializer(), { cached }, { cached = it })
}

object GeometricFormulasRepository {
    @Volatile private var cached: List<GeometricFormula>? = null
    suspend fun load(context: Context): List<GeometricFormula> =
        loadList(context, "geometric_formulas.json", GeometricFormula.serializer(), { cached }, { cached = it })
}
