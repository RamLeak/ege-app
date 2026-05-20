package com.daniel.ege100.srs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 5 Stage E1 — юнит-тесты SM-2.
 *
 * Запуск: `gradlew :app:testDebugUnitTest` (или `:app:test`).
 * Тесты гоняются на чистом JVM, без эмулятора.
 */
class SrsAlgorithmTest {

    private val now = 1_700_000_000_000L
    private val day = SrsAlgorithm.DAY_MILLIS

    @Test
    fun `new card with grade=4 keeps EF, interval=1, reps=1`() {
        val r = SrsAlgorithm.computeNextReview(
            grade = 4,
            currentEf = SrsAlgorithm.INITIAL_EF,
            currentInterval = 0,
            currentReps = 0,
            nowMillis = now,
        )
        // grade=4 → delta = 0.1 - 1*(0.08 + 1*0.02) = 0.0; EF не меняется.
        assertEquals(2.5, r.easeFactor, 1e-9)
        assertEquals(1, r.intervalDays)
        assertEquals(1, r.repetitions)
        assertEquals(now + day, r.nextReviewAt)
    }

    @Test
    fun `second success with grade=4 yields interval=6`() {
        // После первого успеха карточка имеет reps=1, interval=1, EF=2.5.
        val r = SrsAlgorithm.computeNextReview(
            grade = 4,
            currentEf = 2.5,
            currentInterval = 1,
            currentReps = 1,
            nowMillis = now,
        )
        assertEquals(6, r.intervalDays)
        assertEquals(2, r.repetitions)
        assertEquals(now + 6 * day, r.nextReviewAt)
    }

    @Test
    fun `third success with grade=4 yields interval 15`() {
        // reps=2 → newReps=3 → interval = round(6 * 2.5) = 15.
        val r = SrsAlgorithm.computeNextReview(
            grade = 4,
            currentEf = 2.5,
            currentInterval = 6,
            currentReps = 2,
            nowMillis = now,
        )
        assertEquals(15, r.intervalDays)
        assertEquals(3, r.repetitions)
    }

    @Test
    fun `grade=0 resets repetitions and interval to 1`() {
        // Карточка в зрелом состоянии, пользователь забыл.
        val r = SrsAlgorithm.computeNextReview(
            grade = 0,
            currentEf = 2.5,
            currentInterval = 15,
            currentReps = 3,
            nowMillis = now,
        )
        assertEquals(1, r.intervalDays)
        assertEquals(0, r.repetitions)
        assertEquals(now + day, r.nextReviewAt)
        // EF понижается: delta = 0.1 - 5*(0.08 + 5*0.02) = -0.8. 2.5 - 0.8 = 1.7.
        assertEquals(1.7, r.easeFactor, 1e-9)
    }

    @Test
    fun `EF never drops below MIN_EF after repeated failures`() {
        // Серия grade=0: 2.5 → 1.7 → 0.9 (clamp to 1.3) → 0.5 (clamp to 1.3) → ...
        var ef = SrsAlgorithm.INITIAL_EF
        repeat(10) {
            val r = SrsAlgorithm.computeNextReview(
                grade = 0,
                currentEf = ef,
                currentInterval = 1,
                currentReps = 0,
                nowMillis = now,
            )
            ef = r.easeFactor
            assertTrue("EF must stay >= MIN_EF, got $ef", ef >= SrsAlgorithm.MIN_EF)
        }
        assertEquals(SrsAlgorithm.MIN_EF, ef, 1e-9)
    }

    @Test
    fun `grade=5 increases EF more than grade=4`() {
        val rGrade4 = SrsAlgorithm.computeNextReview(4, 2.5, 0, 0, now)
        val rGrade5 = SrsAlgorithm.computeNextReview(5, 2.5, 0, 0, now)
        // grade=4: delta=0, EF=2.5. grade=5: delta=0.1, EF=2.6.
        assertEquals(2.5, rGrade4.easeFactor, 1e-9)
        assertEquals(2.6, rGrade5.easeFactor, 1e-9)
        assertTrue(rGrade5.easeFactor > rGrade4.easeFactor)
    }

    @Test
    fun `grade=3 success but small EF penalty`() {
        // grade=3 → delta = 0.1 - 2*(0.08 + 2*0.02) = -0.14. EF: 2.5 → 2.36.
        val r = SrsAlgorithm.computeNextReview(3, 2.5, 0, 0, now)
        assertEquals(2.36, r.easeFactor, 1e-9)
        assertEquals(1, r.intervalDays)  // первый успех → interval=1
        assertEquals(1, r.repetitions)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `grade=1 is rejected`() {
        SrsAlgorithm.computeNextReview(1, 2.5, 0, 0, now)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `grade=2 is rejected`() {
        SrsAlgorithm.computeNextReview(2, 2.5, 0, 0, now)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `grade=6 is rejected`() {
        SrsAlgorithm.computeNextReview(6, 2.5, 0, 0, now)
    }

    @Test
    fun `nextReviewAt is nowMillis plus intervalDays times DAY_MILLIS`() {
        val r = SrsAlgorithm.computeNextReview(
            grade = 4,
            currentEf = 2.5,
            currentInterval = 6,
            currentReps = 2,
            nowMillis = now,
        )
        assertEquals(15, r.intervalDays)
        assertEquals(now + 15L * day, r.nextReviewAt)
    }

    @Test
    fun `mature card progression grade=4`() {
        // Симулируем цепочку успешных reviews: 1, 6, 15, 38, 95, ...
        var ef = SrsAlgorithm.INITIAL_EF
        var interval = 0
        var reps = 0
        val intervals = mutableListOf<Int>()
        repeat(5) {
            val r = SrsAlgorithm.computeNextReview(4, ef, interval, reps, now)
            ef = r.easeFactor
            interval = r.intervalDays
            reps = r.repetitions
            intervals += interval
        }
        // 1 (reps=1) → 6 (reps=2) → 15 (reps=3) → 38 (reps=4) → 95 (reps=5)
        assertEquals(listOf(1, 6, 15, 38, 95), intervals)
    }
}
