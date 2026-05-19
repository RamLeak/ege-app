package com.daniel.ege100.data

import android.content.Context

/**
 * Phase 4 Stage P4-D2 part Б (Convention #65) — расчёт прогресса по типам
 * и подвидам через **два запроса в две БД** (corpus.db + user_data.db).
 *
 * Почему не один SQL: `problems`/`problem_types` лежат в `corpus.db`
 * (read-only, поставляется с APK), а `attempt_log` — в `user_data.db`
 * (writable). Room не поддерживает JOIN между БД, поэтому:
 *   1. Достаём problem_id'ы из corpus.db (по type_id или subtype_id).
 *   2. Спрашиваем attempt_log.getLastCorrectIds(ids) — сколько последних
 *      попыток вернули is_correct=1.
 *   3. Считаем solved/total на Kotlin.
 *
 * IN-клозы на 500-2000 ID за раз — это нормально для SQLite-Room (лимит
 * SQLITE_MAX_VARIABLE_NUMBER = 999 по умолчанию, но в Android — 500;
 * фактически для типа в среднем 200-300 задач, для подвида 30-60).
 * При очень больших списках можно бить на chunks(500), но пока не надо.
 */
object ProgressRepository {

    data class TypeProgress(
        val typeId: Long,
        val typeNumber: Int,
        val total: Int,
        val solved: Int,
    ) {
        val ratio: Float = if (total > 0) solved.toFloat() / total else 0f
        val isMastered: Boolean = total > 0 && solved == total
    }

    data class SubtypeProgress(
        val subtypeId: Long,
        val total: Int,
        val solved: Int,
    ) {
        val ratio: Float = if (total > 0) solved.toFloat() / total else 0f
        val isMastered: Boolean = total > 0 && solved == total
    }

    /**
     * Прогресс по всем не-supplementary типам предмета. Возвращает map
     * typeId → progress, чтобы UI быстро искал.
     */
    suspend fun getTypeProgress(context: Context, subjectId: Long): Map<Long, TypeProgress> {
        val catalogDao = EgeDatabase.get(context).catalogDao()
        val attemptDao = UserDataDatabase.get(context).attemptLogDao()
        val types = catalogDao.getTypesBySubject(subjectId)
        val result = mutableMapOf<Long, TypeProgress>()
        for (t in types) {
            val ids = catalogDao.getProblemIdsByType(t.id)
            val solved = if (ids.isEmpty()) 0
            else attemptDao.getLastCorrectIds(ids).size
            result[t.id] = TypeProgress(
                typeId = t.id,
                typeNumber = t.number,
                total = t.problemCount,
                solved = solved,
            )
        }
        return result
    }

    /** Прогресс по всем подвидам типа. */
    suspend fun getSubtypeProgress(context: Context, typeId: Long): Map<Long, SubtypeProgress> {
        val catalogDao = EgeDatabase.get(context).catalogDao()
        val attemptDao = UserDataDatabase.get(context).attemptLogDao()
        val subtypes = catalogDao.getSubtypesByType(typeId)
        val result = mutableMapOf<Long, SubtypeProgress>()
        for (st in subtypes) {
            val ids = catalogDao.getProblemIdsBySubtype(st.id)
            val solved = if (ids.isEmpty()) 0
            else attemptDao.getLastCorrectIds(ids).size
            result[st.id] = SubtypeProgress(
                subtypeId = st.id,
                total = st.problemCount,
                solved = solved,
            )
        }
        return result
    }

    /**
     * Phase 4 Stage P4-D2 part В (Convention #66) — typesCovered по
     * новой метрике: тип «освоен» только если ВСЕ задачи решены правильно.
     *
     * Возвращает количество типов где `total > 0 && solved == total`.
     * Суммируется по обоим предметам (math + rus).
     */
    suspend fun computeTypesCoveredFull(context: Context): Int {
        val catalogDao = EgeDatabase.get(context).catalogDao()
        val mathSubject = catalogDao.getSubjectBySlug("mathb")?.id
        val rusSubject = catalogDao.getSubjectBySlug("rus")?.id
        var count = 0
        if (mathSubject != null) {
            count += getTypeProgress(context, mathSubject).values.count { it.isMastered }
        }
        if (rusSubject != null) {
            count += getTypeProgress(context, rusSubject).values.count { it.isMastered }
        }
        return count
    }
}
