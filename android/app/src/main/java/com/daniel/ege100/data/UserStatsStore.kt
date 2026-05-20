package com.daniel.ege100.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
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
    /**
     * Phase 4 Stage P4-C part Е1 (Convention #54) — счётчик правильно
     * отгаданных слов в тренажёрах (ударения + пропуски). Виден в
     * AchievementsRow как «Слов выучено».
     */
    val trainerWordsLearned: Int = 0,
    /**
     * Phase 4 Stage P4-D (Convention #76) — имена пройденных тренажёров.
     * "Пройден" = пользователь дошёл до последнего слова/задачи. UI показывает
     * «Тренажёров пройдено: N из 18».
     */
    val trainersCompleted: Set<String> = emptySet(),
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

    // Phase 4 Stage P4-C part Е1 (Convention #54).
    private val TRAINER_WORDS_LEARNED = intPreferencesKey("trainer_words_learned")

    // Phase 4 Stage P4-D (Convention #76).
    private val TRAINERS_COMPLETED = stringSetPreferencesKey("trainers_completed")

    /**
     * Полный список всех 20 тренажёров приложения (для подсчёта "N из 20" и
     * для UI «Все тренажёры»). При добавлении нового тренажёра — расширить.
     */
    val ALL_TRAINER_IDS: List<String> = listOf(
        "accent_nouns", "accent_adjectives", "accent_verbs",
        "accent_participles", "accent_gerunds", "accent_adverbs",
        "accent_all_alphabetical", "accent_all_random",
        "wordblank_t9", "wordblank_t10", "wordblank_t11", "wordblank_t12",
        "paronym", "pleonasm",
        // Phase 4 Stage P4-D5 (Convention #83): rus.7 = словосочетания.
        // Phase 4 Stage P4-D6 (Convention #90): "rus_grammar" удалён (тренажёр №8
        // полностью удалён). Если в Set<String> trainers_completed у пользователя
        // осталась запись — она просто игнорируется (UI её нигде не покажет).
        "rus_collocation",
        "math_trig", "math_shortmult", "math_logpower",
        "math_derivatives", "math_geometry",
    )

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

    /**
     * Phase 5 perf fix P3 (tag `phase-5-fix-2-stats-perf`) — batch read.
     *
     * Раньше `SubtypeStatsRepository.getStatsForSubject` вызывал
     * `getSubtypeStats(sub.id)` для каждого подвида (50 math + 27 rus =
     * 77 итераций), и каждый вызов делал отдельный `prefs.first()` —
     * 77 suspend reads из DataStore. Это занимало 200-500 ms на refresh.
     *
     * Новый метод делает **ОДИН** `prefs.first()` и парсит ВСЕ ключи
     * `subtype_total_<id>` + `subtype_correct_<id>` в один Map<Long, (total, correct)>.
     * Repository потом делает синхронный map.get(subtypeId) для каждого подвида —
     * O(1) вместо O(N) suspend reads.
     *
     * Время до фикса: ~200-500 ms (зависит от кол-ва подвидов в БД).
     * Время после фикса: <20 ms (один DataStore read + map build).
     */
    suspend fun getAllSubtypeStats(context: Context): Map<Long, Pair<Int, Int>> {
        val prefs = context.userStatsStore.data.first()
        val totals = mutableMapOf<Long, Int>()
        val corrects = mutableMapOf<Long, Int>()
        for ((key, raw) in prefs.asMap()) {
            val name = key.name
            val v = raw as? Int ?: continue
            when {
                name.startsWith(SUBTYPE_TOTAL_PREFIX) -> {
                    val id = name.removePrefix(SUBTYPE_TOTAL_PREFIX).toLongOrNull() ?: continue
                    totals[id] = v
                }
                name.startsWith(SUBTYPE_CORRECT_PREFIX) -> {
                    val id = name.removePrefix(SUBTYPE_CORRECT_PREFIX).toLongOrNull() ?: continue
                    corrects[id] = v
                }
            }
        }
        // Объединяем: все id, у которых есть либо total либо correct.
        val allIds = totals.keys + corrects.keys
        return allIds.associateWith { id ->
            (totals[id] ?: 0) to (corrects[id] ?: 0)
        }
    }

    /**
     * Phase 4 Stage P4-C part Е1 (Convention #54): инкремент счётчика
     * правильно отгаданных слов в тренажёрах. Зовётся в AccentTrainer и
     * WordBlankTrainer **только при isCorrect=true**.
     */
    suspend fun incrementTrainerWordsLearned(context: Context) {
        context.userStatsStore.edit { prefs ->
            prefs[TRAINER_WORDS_LEARNED] = (prefs[TRAINER_WORDS_LEARNED] ?: 0) + 1
        }
    }

    suspend fun getTrainerWordsLearned(context: Context): Int =
        context.userStatsStore.data.first()[TRAINER_WORDS_LEARNED] ?: 0

    fun trainerWordsLearnedFlow(context: Context): Flow<Int> =
        context.userStatsStore.data.map { it[TRAINER_WORDS_LEARNED] ?: 0 }

    /** Phase 4 Stage P4-D (Convention #76): пометить тренажёр пройденным. */
    suspend fun markTrainerCompleted(context: Context, trainerId: String) {
        context.userStatsStore.edit { prefs ->
            val current = prefs[TRAINERS_COMPLETED] ?: emptySet()
            prefs[TRAINERS_COMPLETED] = current + trainerId
        }
    }

    suspend fun getTrainersCompleted(context: Context): Set<String> =
        context.userStatsStore.data.first()[TRAINERS_COMPLETED] ?: emptySet()

    fun trainersCompletedFlow(context: Context): Flow<Set<String>> =
        context.userStatsStore.data.map { it[TRAINERS_COMPLETED] ?: emptySet() }

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
        val trainerWords = prefs[TRAINER_WORDS_LEARNED] ?: 0
        val completed = prefs[TRAINERS_COMPLETED] ?: emptySet()
        return UserStatsSnapshot(
            typeStats = typeStats.mapValues { it.value.toMap() },
            subtypeStats = subtypeStats.toMap(),
            trainerWordsLearned = trainerWords,
            trainersCompleted = completed,
        )
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
            // Phase 4 Stage P4-C part Е1.
            prefs[TRAINER_WORDS_LEARNED] = snapshot.trainerWordsLearned
            // Phase 4 Stage P4-D (Convention #76).
            prefs[TRAINERS_COMPLETED] = snapshot.trainersCompleted
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
