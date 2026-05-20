package com.daniel.ege100.srs

import kotlin.math.roundToInt

/**
 * Phase 5 Stage E1 — SM-2 (SuperMemo-2, Anki-style).
 *
 * Pure Kotlin: ни Android, ни Room — чтобы юнит-тесты гонялись на JVM без эмулятора.
 *
 * Используются только 4 оценки (grade): 0 / 3 / 4 / 5. Градации 1, 2 в SM-2 исторические,
 * слишком близки к 0, и пользователю их сложно различать — мы их не показываем в UI.
 *
 * - grade=0  → «забыл»: интервал=1 день, repetitions сбрасывается, EF понижается (но ≥ MIN_EF).
 * - grade=3  → «с трудом»: успех, но небольшой штраф к EF.
 * - grade=4  → «нормально»: успех, EF близок к нынешнему.
 * - grade=5  → «легко»: успех, EF растёт.
 */
object SrsAlgorithm {

    const val MIN_EF: Double = 1.3
    const val INITIAL_EF: Double = 2.5
    const val DAY_MILLIS: Long = 24L * 60 * 60 * 1000

    /**
     * Результат проверки карточки: новое состояние SM-2 + момент следующего повторения.
     */
    data class ReviewResult(
        val easeFactor: Double,
        val intervalDays: Int,
        val repetitions: Int,
        val nextReviewAt: Long,
    )

    /**
     * Применяет SM-2 для одной карточки.
     *
     * @param grade оценка пользователя. Допустимы 0, 3, 4, 5.
     * @param currentEf текущий ease factor (для свежей карточки = INITIAL_EF).
     * @param currentInterval текущий интервал в днях (для свежей карточки = 0 или 1).
     * @param currentReps текущее число успешных подряд повторений.
     * @param nowMillis текущее время в epoch millis.
     */
    fun computeNextReview(
        grade: Int,
        currentEf: Double,
        currentInterval: Int,
        currentReps: Int,
        nowMillis: Long,
    ): ReviewResult {
        require(grade in setOf(0, 3, 4, 5)) {
            "grade must be one of {0, 3, 4, 5}, got $grade"
        }

        val newEf = updateEaseFactor(currentEf, grade)

        if (grade < 3) {
            return ReviewResult(
                easeFactor = newEf,
                intervalDays = 1,
                repetitions = 0,
                nextReviewAt = nowMillis + DAY_MILLIS,
            )
        }

        val newReps = currentReps + 1
        val newInterval = when (newReps) {
            1 -> 1
            2 -> 6
            else -> (currentInterval * currentEf).roundToInt().coerceAtLeast(1)
        }

        return ReviewResult(
            easeFactor = newEf,
            intervalDays = newInterval,
            repetitions = newReps,
            nextReviewAt = nowMillis + newInterval * DAY_MILLIS,
        )
    }

    /**
     * Каноническая формула SM-2:
     *   EF' = EF + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02))
     * с нижней границей MIN_EF (1.3).
     */
    private fun updateEaseFactor(currentEf: Double, grade: Int): Double {
        val q = grade.toDouble()
        val delta = 0.1 - (5 - q) * (0.08 + (5 - q) * 0.02)
        return (currentEf + delta).coerceAtLeast(MIN_EF)
    }
}
