package com.daniel.ege100.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

/**
 * Phase 3 Stage B part Г — счётчики попыток и правильных по типам и подвидам.
 *
 * Ключи (стабильный формат):
 *   type_total_<subjectSlug>_<typeNumber>      → Int
 *   type_correct_<subjectSlug>_<typeNumber>    → Int
 *   subtype_total_<subtypeId>                  → Int
 *   subtype_correct_<subtypeId>                → Int
 *
 * subjectSlug: "math" для mathb-задач (короче ключи, mapping в recordAttempt
 * происходит из вызывающего ViewModel), "rus" для русских. Тренажёры ударений
 * → subject="rus", typeNumber=4. Тренажёры орфографии → subject="rus",
 * typeNumber=9..12.
 *
 * Stage P3-D расширим: добавим last_attempt_at для SRS-логики.
 */
@Serializable
data class TypeAccuracy(
    val typeNumber: Int,
    val attempts: Int,
    val correct: Int,
    val accuracy: Float,  // 0..1
)

@Serializable
data class UserStatsSnapshot(
    /** subject → (typeNumber → "total:correct") */
    val typeStats: Map<String, Map<Int, String>> = emptyMap(),
    /** subtypeId → "total:correct" */
    val subtypeStats: Map<Long, String> = emptyMap(),
)

private val Context.userStatsStore by preferencesDataStore("user_stats")

object UserStatsStore {
    private const val TYPE_TOTAL_PREFIX = "type_total_"
    private const val TYPE_CORRECT_PREFIX = "type_correct_"
    private const val SUBTYPE_TOTAL_PREFIX = "subtype_total_"
    private const val SUBTYPE_CORRECT_PREFIX = "subtype_correct_"

    private fun typeTotalKey(subject: String, n: Int) =
        intPreferencesKey("$TYPE_TOTAL_PREFIX${subject}_$n")

    private fun typeCorrectKey(subject: String, n: Int) =
        intPreferencesKey("$TYPE_CORRECT_PREFIX${subject}_$n")

    private fun subtypeTotalKey(id: Long) = intPreferencesKey("$SUBTYPE_TOTAL_PREFIX$id")
    private fun subtypeCorrectKey(id: Long) = intPreferencesKey("$SUBTYPE_CORRECT_PREFIX$id")

    suspend fun recordAttempt(
        context: Context,
        subject: String,
        typeNumber: Int,
        subtypeId: Long? = null,
        isCorrect: Boolean,
    ) {
        context.userStatsStore.edit { prefs ->
            val tT = typeTotalKey(subject, typeNumber)
            val tC = typeCorrectKey(subject, typeNumber)
            prefs[tT] = (prefs[tT] ?: 0) + 1
            if (isCorrect) prefs[tC] = (prefs[tC] ?: 0) + 1

            if (subtypeId != null) {
                val sT = subtypeTotalKey(subtypeId)
                val sC = subtypeCorrectKey(subtypeId)
                prefs[sT] = (prefs[sT] ?: 0) + 1
                if (isCorrect) prefs[sC] = (prefs[sC] ?: 0) + 1
            }
        }
    }

    /** Возвращает stats для всех типов 1..maxN (включая пустые). */
    suspend fun getTypeStats(context: Context, subject: String, maxN: Int): List<TypeAccuracy> {
        val prefs = context.userStatsStore.data.first()
        return (1..maxN).map { n ->
            val total = prefs[typeTotalKey(subject, n)] ?: 0
            val correct = prefs[typeCorrectKey(subject, n)] ?: 0
            TypeAccuracy(
                typeNumber = n,
                attempts = total,
                correct = correct,
                accuracy = if (total > 0) correct.toFloat() / total else 0f,
            )
        }
    }

    /** Возвращает (total, correct) для конкретного подвида. */
    suspend fun getSubtypeStats(context: Context, subtypeId: Long): Pair<Int, Int> {
        val prefs = context.userStatsStore.data.first()
        val total = prefs[subtypeTotalKey(subtypeId)] ?: 0
        val correct = prefs[subtypeCorrectKey(subtypeId)] ?: 0
        return total to correct
    }

    /** Flow для подписки в UI (например на главном экране). */
    fun typeStatsFlow(context: Context, subject: String, maxN: Int): Flow<List<TypeAccuracy>> =
        context.userStatsStore.data.map { prefs ->
            (1..maxN).map { n ->
                val total = prefs[typeTotalKey(subject, n)] ?: 0
                val correct = prefs[typeCorrectKey(subject, n)] ?: 0
                TypeAccuracy(n, total, correct, if (total > 0) correct.toFloat() / total else 0f)
            }
        }

    // ----- Backup / restore -----

    suspend fun snapshot(context: Context): UserStatsSnapshot {
        val prefs = context.userStatsStore.data.first()
        val typeStats = mutableMapOf<String, MutableMap<Int, String>>()
        val subtypeStats = mutableMapOf<Long, String>()
        for ((key, raw) in prefs.asMap()) {
            val name = key.name
            val v = raw as? Int ?: continue
            when {
                name.startsWith(TYPE_TOTAL_PREFIX) -> {
                    // type_total_<subject>_<n>
                    val rest = name.removePrefix(TYPE_TOTAL_PREFIX)
                    val (subject, nStr) = rest.split('_', limit = 2).let { it[0] to it.getOrNull(1) }
                    val n = nStr?.toIntOrNull() ?: continue
                    val map = typeStats.getOrPut(subject) { mutableMapOf() }
                    val cur = map[n]
                    val correct = cur?.substringAfter(':', "0")?.toIntOrNull() ?: 0
                    map[n] = "$v:$correct"
                }
                name.startsWith(TYPE_CORRECT_PREFIX) -> {
                    val rest = name.removePrefix(TYPE_CORRECT_PREFIX)
                    val (subject, nStr) = rest.split('_', limit = 2).let { it[0] to it.getOrNull(1) }
                    val n = nStr?.toIntOrNull() ?: continue
                    val map = typeStats.getOrPut(subject) { mutableMapOf() }
                    val cur = map[n]
                    val total = cur?.substringBefore(':', "0")?.toIntOrNull() ?: 0
                    map[n] = "$total:$v"
                }
                name.startsWith(SUBTYPE_TOTAL_PREFIX) -> {
                    val id = name.removePrefix(SUBTYPE_TOTAL_PREFIX).toLongOrNull() ?: continue
                    val cur = subtypeStats[id]
                    val correct = cur?.substringAfter(':', "0")?.toIntOrNull() ?: 0
                    subtypeStats[id] = "$v:$correct"
                }
                name.startsWith(SUBTYPE_CORRECT_PREFIX) -> {
                    val id = name.removePrefix(SUBTYPE_CORRECT_PREFIX).toLongOrNull() ?: continue
                    val cur = subtypeStats[id]
                    val total = cur?.substringBefore(':', "0")?.toIntOrNull() ?: 0
                    subtypeStats[id] = "$total:$v"
                }
            }
        }
        return UserStatsSnapshot(typeStats.mapValues { it.value.toMap() }, subtypeStats.toMap())
    }

    suspend fun restore(context: Context, snapshot: UserStatsSnapshot) {
        context.userStatsStore.edit { prefs ->
            prefs.clear()
            for ((subject, map) in snapshot.typeStats) {
                for ((n, value) in map) {
                    val (total, correct) = parseTC(value)
                    prefs[typeTotalKey(subject, n)] = total
                    prefs[typeCorrectKey(subject, n)] = correct
                }
            }
            for ((id, value) in snapshot.subtypeStats) {
                val (total, correct) = parseTC(value)
                prefs[subtypeTotalKey(id)] = total
                prefs[subtypeCorrectKey(id)] = correct
            }
        }
    }

    suspend fun clearAll(context: Context) {
        context.userStatsStore.edit { it.clear() }
    }

    private fun parseTC(value: String): Pair<Int, Int> {
        val parts = value.split(':')
        val total = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val correct = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return total to correct
    }
}
