package com.daniel.ege100.data

import android.content.Context

/**
 * Phase 3 Stage B part Г — ФИПИ-таблица «первичный балл → тестовый балл».
 *
 * Данные из приказа ФИПИ-2024 (актуально для ЕГЭ-2025-2027 при сохранении
 * структуры варианта). Источник:
 *   - Математика профильная: 0..32 первичных → 0..100 тестовых.
 *   - Русский: 0..50 первичных → 0..100 тестовых.
 *
 * Если ФИПИ изменит таблицу — обновить здесь руками.
 */
object FipiScoreTable {
    private val mathTable = listOf(
        0 to 0, 1 to 5, 2 to 9, 3 to 14, 4 to 18, 5 to 23,
        6 to 27, 7 to 33, 8 to 39, 9 to 45, 10 to 50,
        11 to 56, 12 to 62, 13 to 68, 14 to 70, 15 to 72,
        16 to 74, 17 to 76, 18 to 78, 19 to 80, 20 to 82,
        21 to 84, 22 to 86, 23 to 88, 24 to 90, 25 to 92,
        26 to 94, 27 to 96, 28 to 97, 29 to 98, 30 to 99,
        31 to 100, 32 to 100,
    )

    private val rusTable = listOf(
        0 to 0, 5 to 9, 10 to 18, 15 to 27, 20 to 35,
        22 to 38, 25 to 42, 28 to 46, 30 to 49, 33 to 53,
        35 to 56, 37 to 59, 40 to 64, 42 to 67, 45 to 72,
        47 to 75, 48 to 76, 49 to 77, 50 to 78,
    )

    /**
     * Округляем raw вниз до ближайшего записанного значения и берём
     * соответствующий тестовый балл.
     */
    fun rawToTest(subject: String, raw: Int): Int {
        val table = if (subject == "math") mathTable else rusTable
        val capped = raw.coerceAtLeast(0)
        // Находим максимальный ключ <= capped.
        var result = 0
        for ((r, t) in table) {
            if (r <= capped) result = t else break
        }
        return result
    }

    /** Максимальный первичный балл для предмета (для прогресс-бара покрытия). */
    fun maxRaw(subject: String): Int = if (subject == "math") 32 else 50
}

/**
 * Phase 3 Stage B part Г — алгоритм прогноза балла.
 *
 * Идея: для каждого типа задач ЕГЭ оцениваем «ожидаемый процент верного
 * ответа в реальном пробнике» через текущую accuracy + покрытие (coverage =
 * attempts/15, capped at 1). При малом coverage смешиваем с pessimistic
 * estimate 30% — чтобы не получить «100% prediction» по 1 решённой задаче.
 *
 * Каждый тип имеет «вес» = максимальный первичный балл за него
 * (типы №1-12 в math = 1 балл, №13-15 = 2, №16-17 = 3, №18-19 = 4; в rus
 * №27 сочинение = 24 балла, остальные = 1 балл).
 */
data class PredictorResult(
    val testScore: Int,
    val rawScore: Int,
    val maxRaw: Int,
    val confidence: Float,  // 0..1 — средний coverage
    val weakestTypes: List<TypeAccuracy>,
)

object ScorePredictor {
    private fun mathWeight(typeNumber: Int): Int = when (typeNumber) {
        in 1..12 -> 1
        in 13..15 -> 2
        in 16..17 -> 3
        in 18..19 -> 4
        else -> 1
    }

    private fun rusWeight(typeNumber: Int): Int = when (typeNumber) {
        27 -> 24
        in 1..26 -> 1
        else -> 1
    }

    suspend fun predictMath(context: Context): PredictorResult =
        predict(context, subject = "math", maxN = 19, weightFor = ::mathWeight)

    suspend fun predictRus(context: Context): PredictorResult =
        predict(context, subject = "rus", maxN = 27, weightFor = ::rusWeight)

    private suspend fun predict(
        context: Context,
        subject: String,
        maxN: Int,
        weightFor: (Int) -> Int,
    ): PredictorResult {
        val typeStats = UserStatsStore.getTypeStats(context, subject, maxN)

        var expectedRaw = 0.0
        var totalCoverage = 0f
        for (stat in typeStats) {
            val weight = weightFor(stat.typeNumber)
            val coverage = (stat.attempts.toFloat() / 15f).coerceIn(0f, 1f)

            // Базовая оценка «процента» = функция от accuracy.
            val basePercent = when {
                stat.attempts == 0 -> 0.30  // pessimistic при отсутствии данных
                stat.accuracy >= 0.7f -> 0.85
                stat.accuracy >= 0.5f -> 0.55
                stat.accuracy >= 0.3f -> 0.30
                else -> 0.10
            }

            // При низком coverage смешиваем с pessimistic 30%.
            val mixedPercent = basePercent * coverage + 0.30 * (1 - coverage)
            expectedRaw += weight * mixedPercent
            totalCoverage += coverage
        }

        val avgCoverage = (totalCoverage / maxN).coerceIn(0f, 1f)
        val rawInt = expectedRaw.toInt().coerceIn(0, FipiScoreTable.maxRaw(subject))
        val testScore = FipiScoreTable.rawToTest(subject, rawInt)

        val weakest = typeStats
            .filter { it.attempts >= 5 }
            .sortedBy { it.accuracy }
            .take(3)

        return PredictorResult(
            testScore = testScore,
            rawScore = rawInt,
            maxRaw = FipiScoreTable.maxRaw(subject),
            confidence = avgCoverage,
            weakestTypes = weakest,
        )
    }
}
