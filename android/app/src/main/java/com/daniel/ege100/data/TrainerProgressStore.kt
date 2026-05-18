package com.daniel.ege100.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Stage 5 part А — память тренажёров.
 *
 * При каждой смене позиции в тренажёре сохраняем JSON-снэпшот: trainerId →
 * { position, total, order, indices }. При повторном заходе показываем
 * ResumeBottomSheet с выбором «Продолжить / Начать сначала». При завершении
 * (последнее слово прошло) — clearProgress, чтобы следующий заход начинался
 * с нуля без bottom sheet.
 *
 * trainerId формат:
 *   accent_<categoryId> | accent_all_random | accent_all_alphabetical
 *   blank_<typeNumber>
 */
@Serializable
data class TrainerProgress(
    val position: Int,
    val total: Int,
    val order: String,
    val indices: List<Int>,
)

private val Context.trainerProgressStore by preferencesDataStore("trainer_progress")

object TrainerProgressStore {
    private val json = Json { ignoreUnknownKeys = true }
    private fun keyFor(trainerId: String) = stringPreferencesKey("progress_$trainerId")

    suspend fun get(context: Context, trainerId: String): TrainerProgress? {
        val raw = context.trainerProgressStore.data
            .map { it[keyFor(trainerId)] }
            .first() ?: return null
        return runCatching {
            json.decodeFromString(TrainerProgress.serializer(), raw)
        }.getOrNull()
    }

    suspend fun save(context: Context, trainerId: String, progress: TrainerProgress) {
        val encoded = json.encodeToString(TrainerProgress.serializer(), progress)
        context.trainerProgressStore.edit { it[keyFor(trainerId)] = encoded }
    }

    suspend fun clear(context: Context, trainerId: String) {
        context.trainerProgressStore.edit { it.remove(keyFor(trainerId)) }
    }

    /** Phase 3 Stage A part Д: снимок всех текущих прогрессов для бэкапа. */
    suspend fun getAllProgress(context: Context): Map<String, TrainerProgress> {
        val data = context.trainerProgressStore.data.first()
        val prefix = "progress_"
        val result = mutableMapOf<String, TrainerProgress>()
        for ((key, raw) in data.asMap()) {
            val name = key.name
            if (!name.startsWith(prefix) || raw !is String) continue
            val trainerId = name.removePrefix(prefix)
            runCatching {
                json.decodeFromString(TrainerProgress.serializer(), raw)
            }.getOrNull()?.let { result[trainerId] = it }
        }
        return result
    }

    /** Phase 3 Stage A part Д: восстановление всех прогрессов из бэкапа. */
    suspend fun restoreAll(context: Context, snapshot: Map<String, TrainerProgress>) {
        context.trainerProgressStore.edit { prefs ->
            // Удаляем все progress_* ключи перед записью.
            val toRemove = prefs.asMap().keys.filter { it.name.startsWith("progress_") }
            toRemove.forEach { prefs.remove(it) }
            for ((trainerId, progress) in snapshot) {
                prefs[keyFor(trainerId)] = json.encodeToString(TrainerProgress.serializer(), progress)
            }
        }
    }

    /** Phase 3 Stage A part Д: сброс всех прогрессов. */
    suspend fun clearAll(context: Context) {
        context.trainerProgressStore.edit { prefs ->
            val toRemove = prefs.asMap().keys.filter { it.name.startsWith("progress_") }
            toRemove.forEach { prefs.remove(it) }
        }
    }
}
