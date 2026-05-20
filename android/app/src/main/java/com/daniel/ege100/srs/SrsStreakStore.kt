package com.daniel.ege100.srs

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

/**
 * Phase 5 Stage E4 (§1.7) — отдельный SRS-streak.
 *
 * День считается успешным, если в нём пользователь поставил хотя бы одной
 * карточке оценку ≥ 3 (успех). Streak инкрементируется при первой успешной
 * оценке в дне, не уже-засчитанным:
 *
 *   lastReviewDate == today → ничего не делаем (счётчик не дёргаем дважды).
 *   lastReviewDate == today-1 → currentStreak += 1.
 *   gap > 1 день → currentStreak = 1 (новая серия).
 *
 * MaxStreak хранится отдельно; reset через DangerButton в Settings обнуляет
 * current + max.
 *
 * НЕ путать с обычным StreakStore (daily problem solving): у обычного
 * правило «10 задач за день», здесь — хотя бы одна успешная карточка.
 */
@Serializable
data class SrsStreakState(
    val currentStreak: Int = 0,
    val maxStreak: Int = 0,
    val lastReviewDate: String? = null,  // ISO date "YYYY-MM-DD" или null
)

private val Context.srsStreakStore by preferencesDataStore("srs_streak")

object SrsStreakStore {
    private val CURRENT = intPreferencesKey("current_streak")
    private val MAX = intPreferencesKey("max_streak")
    private val LAST_DATE = stringPreferencesKey("last_review_date")

    fun stateFlow(context: Context): Flow<SrsStreakState> =
        context.srsStreakStore.data.map { prefs ->
            SrsStreakState(
                currentStreak = prefs[CURRENT] ?: 0,
                maxStreak = prefs[MAX] ?: 0,
                lastReviewDate = prefs[LAST_DATE],
            )
        }

    suspend fun snapshot(context: Context): SrsStreakState =
        stateFlow(context).first()

    /**
     * Вызывается из SrsReviewViewModel.submitGrade при grade ≥ 3.
     * Идемпотентно в пределах дня: повторные вызовы за сегодня — no-op.
     */
    suspend fun onSuccessfulReview(
        context: Context,
        today: LocalDate = LocalDate.now(),
    ) {
        context.srsStreakStore.edit { prefs ->
            val lastIso = prefs[LAST_DATE]
            val current = prefs[CURRENT] ?: 0
            val max = prefs[MAX] ?: 0

            val newCurrent = when {
                lastIso == today.toString() -> current  // уже засчитан сегодня
                lastIso == today.minusDays(1).toString() -> current + 1
                else -> 1
            }
            prefs[CURRENT] = newCurrent
            prefs[MAX] = maxOf(max, newCurrent)
            prefs[LAST_DATE] = today.toString()
        }
    }

    /**
     * Проверка валидности при старте Главного экрана. Если последний review
     * был раньше чем вчера → currentStreak обнуляется (max сохраняется).
     */
    suspend fun checkValidity(
        context: Context,
        today: LocalDate = LocalDate.now(),
    ) {
        context.srsStreakStore.edit { prefs ->
            val lastIso = prefs[LAST_DATE] ?: return@edit
            val last = runCatching { LocalDate.parse(lastIso) }.getOrNull() ?: return@edit
            val gap = today.toEpochDay() - last.toEpochDay()
            if (gap > 1L) {
                prefs[CURRENT] = 0
            }
        }
    }

    /** Полный reset streak'а (current + max). */
    suspend fun reset(context: Context) {
        context.srsStreakStore.edit { prefs ->
            prefs[CURRENT] = 0
            prefs[MAX] = 0
            prefs.remove(LAST_DATE)
        }
    }

    /** Phase 5 Stage E5 — BackupSnapshot integration. */
    suspend fun restore(context: Context, state: SrsStreakState) {
        context.srsStreakStore.edit { prefs ->
            prefs[CURRENT] = state.currentStreak
            prefs[MAX] = state.maxStreak
            state.lastReviewDate?.let { prefs[LAST_DATE] = it }
        }
    }
}
