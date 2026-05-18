package com.daniel.ege100.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Phase 3 Stage B part В — streak трекинг (дни подряд с активной подготовкой).
 *
 * Правила:
 *   - День считается «активным» если пользователь решил ≥ DAILY_GOAL задач.
 *   - При достижении DAILY_GOAL ровно сегодня → streak увеличивается на 1
 *     (если вчера тоже был активным), либо обнуляется до 1 (если был пропуск).
 *   - Если пропущен ровно 1 день (last_active == today - 2) — streak = 1.
 *   - При вызове `checkValidity()` (на старте Главного экрана) — если между
 *     last_active и today > 1 день, current_streak обнуляется в 0 (но max
 *     сохраняется).
 *
 * `onProblemSolved()` зовётся из всех ViewModel'ей где пользователь даёт
 * ответ: ProblemDetail.checkAnswer, AccentTrainer (при Verdict), WordBlank.checkAnswer.
 */
const val DAILY_GOAL: Int = 10

@Serializable
data class StreakState(
    val currentStreak: Int = 0,
    val maxStreak: Int = 0,
    val todaySolvedCount: Int = 0,
    val lastActiveDate: String? = null,  // ISO YYYY-MM-DD
)

private val Context.streakStore by preferencesDataStore("streak")

object StreakStore {
    private val CURRENT = intPreferencesKey("current")
    private val MAX = intPreferencesKey("max")
    private val LAST_ACTIVE = stringPreferencesKey("last_active")
    private val TODAY_SOLVED = intPreferencesKey("today_solved")
    private val TODAY_DATE = stringPreferencesKey("today_date")

    fun stateFlow(context: Context): Flow<StreakState> =
        context.streakStore.data.map { prefs ->
            val today = LocalDate.now()
            val savedDate = prefs[TODAY_DATE]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val isToday = savedDate == today
            StreakState(
                currentStreak = prefs[CURRENT] ?: 0,
                maxStreak = prefs[MAX] ?: 0,
                todaySolvedCount = if (isToday) prefs[TODAY_SOLVED] ?: 0 else 0,
                lastActiveDate = prefs[LAST_ACTIVE],
            )
        }

    suspend fun snapshot(context: Context): StreakState = stateFlow(context).first()

    /**
     * Вызывается из ViewModel'ей при любом ответе пользователя в задаче/тренажёре.
     * Логика:
     *   1. Инкрементим today_solved.
     *   2. Если ровно достигли DAILY_GOAL и сегодня ещё НЕ считался активным —
     *      бамп streak (+1 от вчера или 1 если пропуск) и max-streak.
     */
    suspend fun onProblemSolved(context: Context) {
        val today = LocalDate.now()
        context.streakStore.edit { prefs ->
            val savedDate = prefs[TODAY_DATE]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val isToday = savedDate == today
            val currentSolved = if (isToday) prefs[TODAY_SOLVED] ?: 0 else 0
            val newSolved = currentSolved + 1

            prefs[TODAY_SOLVED] = newSolved
            prefs[TODAY_DATE] = today.toString()

            val lastActive = prefs[LAST_ACTIVE]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val alreadyCountedToday = lastActive == today
            if (newSolved == DAILY_GOAL && !alreadyCountedToday) {
                val current = prefs[CURRENT] ?: 0
                val newStreak = when {
                    lastActive == null -> 1
                    lastActive == today.minusDays(1) -> current + 1
                    else -> 1
                }
                prefs[CURRENT] = newStreak
                prefs[MAX] = maxOf(prefs[MAX] ?: 0, newStreak)
                prefs[LAST_ACTIVE] = today.toString()
            }
        }
    }

    /**
     * Phase 3 Stage B part В: проверяется на старте Главного экрана.
     * Если между last_active и сегодня прошло > 1 дня — current обнуляется.
     */
    suspend fun checkValidity(context: Context) {
        val today = LocalDate.now()
        context.streakStore.edit { prefs ->
            val lastActive = prefs[LAST_ACTIVE]?.let {
                runCatching { LocalDate.parse(it) }.getOrNull()
            } ?: return@edit
            val gap = ChronoUnit.DAYS.between(lastActive, today)
            if (gap > 1) prefs[CURRENT] = 0
        }
    }

    /** Phase 3 Stage B part Д: backup. */
    suspend fun restore(context: Context, state: StreakState) {
        context.streakStore.edit { prefs ->
            prefs[CURRENT] = state.currentStreak
            prefs[MAX] = state.maxStreak
            if (state.lastActiveDate != null) {
                prefs[LAST_ACTIVE] = state.lastActiveDate
            } else {
                prefs.remove(LAST_ACTIVE)
            }
            prefs[TODAY_SOLVED] = state.todaySolvedCount
            // today_date — оставляем «сегодня», поскольку todaySolvedCount
            // относится к текущему дню в момент бэкапа. Если backup старый,
            // counter всё равно сбросится при следующей загрузке.
            prefs[TODAY_DATE] = LocalDate.now().toString()
        }
    }

    /** Phase 3 Stage B part Д: reset progress. */
    suspend fun clearAll(context: Context) {
        context.streakStore.edit { it.clear() }
    }
}
