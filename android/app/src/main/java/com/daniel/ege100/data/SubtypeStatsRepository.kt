package com.daniel.ege100.data

import android.content.Context

/**
 * Phase 3 Stage B part Д — данные для радара слабых мест.
 *
 * Severity:
 *   GRAY   — мало данных (<15 попыток). Сектор серый «недостаточно данных».
 *   RED    — accuracy < 60% (после ≥15 попыток).
 *   YELLOW — 60% ≤ accuracy < 80%.
 *   GREEN  — accuracy ≥ 80%.
 *
 * Порог 15 задач взят из CLAUDE Safety Rule #1 — он закрывает «ложную
 * уверенность из-за смещённой выборки» (любимые подвиды).
 */
enum class Severity { GRAY, RED, YELLOW, GREEN }

data class SubtypeAccuracy(
    val subtypeId: Long,
    val typeId: Long,
    val subjectSlug: String,   // "mathb" / "rus" — оригинальный slug из БД
    val typeNumber: Int,
    val subtypeTitle: String,
    val kesCode: String?,
    val attempts: Int,
    val correct: Int,
    val accuracy: Float,
    val severity: Severity,
)

object SubtypeStatsRepository {
    private const val MIN_ATTEMPTS_FOR_COLOR = 15

    /**
     * @param subjectSlug "mathb" или "rus".
     * @param statsSubjectKey "math" или "rus" — тот, что в UserStatsStore
     *        (mathb-задачи там лежат под ключом "math").
     */
    suspend fun getStatsForSubject(
        context: Context,
        dao: CatalogDao,
        subjectSlug: String,
        statsSubjectKey: String,
    ): List<SubtypeAccuracy> {
        val subject = dao.getSubjectBySlug(subjectSlug) ?: return emptyList()
        val subtypes = dao.getSubtypesBySubject(subject.id)
        return subtypes.map { sub ->
            val (total, correct) = UserStatsStore.getSubtypeStats(context, sub.id)
            val accuracy = if (total > 0) correct.toFloat() / total else 0f
            val severity = when {
                total < MIN_ATTEMPTS_FOR_COLOR -> Severity.GRAY
                accuracy < 0.60f -> Severity.RED
                accuracy < 0.80f -> Severity.YELLOW
                else -> Severity.GREEN
            }
            SubtypeAccuracy(
                subtypeId = sub.id,
                typeId = sub.typeId,
                subjectSlug = subjectSlug,
                typeNumber = sub.typeNumber,
                subtypeTitle = sub.title,
                kesCode = sub.kesCode,
                attempts = total,
                correct = correct,
                accuracy = accuracy,
                severity = severity,
            )
        }
    }

    /**
     * Подбор задач для быстрого тренажёра «Решить слабые места».
     *
     * Алгоритм:
     *   1. Берём топ-3 RED подвида по обоим предметам (сортировка по accuracy ASC).
     *   2. Из каждого — до 4 случайных задач через `getRandomProblemsInSubtype`.
     *   3. Если RED нет — берём 3 случайных «недокрытых» подвида (attempts < 15).
     *   4. Возвращаем первые 10.
     */
    suspend fun composeWeakMix(context: Context, dao: CatalogDao): List<Long> {
        val mathStats = getStatsForSubject(context, dao, "mathb", "math")
        val rusStats = getStatsForSubject(context, dao, "rus", "rus")
        val all = mathStats + rusStats

        val redSubtypes = all.filter { it.severity == Severity.RED }
            .sortedBy { it.accuracy }
            .take(3)

        val pool = if (redSubtypes.isNotEmpty()) {
            redSubtypes
        } else {
            all.filter { it.attempts < MIN_ATTEMPTS_FOR_COLOR }
                .shuffled()
                .take(3)
        }

        if (pool.isEmpty()) return emptyList()

        val ids = mutableListOf<Long>()
        for (sub in pool) {
            val probs = dao.getRandomProblemsInSubtype(sub.subtypeId, 4)
            ids.addAll(probs.map { it.id })
            if (ids.size >= 10) break
        }
        return ids.take(10)
    }
}
