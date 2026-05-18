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
 * Phase 3 Stage A part В — профиль пользователя.
 *
 * Хранится в DataStore Preferences. Минимум полей чтобы дать «осмысленный»
 * экран Профиль и считать «сколько дней до ЕГЭ» на главном.
 *
 * Дата ЕГЭ-2027 по умолчанию — 04.06.2027 (это рекомендуемая ФИПИ-дата
 * проведения профильной математики, но пользователь может изменить).
 */
@Serializable
data class UserProfile(
    val name: String = "",
    val birthDate: String? = null,  // ISO формат "YYYY-MM-DD" или null
    val targetScore: Int = 80,
    val examDate: String = DEFAULT_EXAM_DATE,
) {
    companion object {
        const val DEFAULT_EXAM_DATE: String = "2027-06-04"
    }

    /** Распарсенная дата рождения, или null если не задана/невалидна. */
    val birthDateParsed: LocalDate?
        get() = birthDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** Распарсенная дата ЕГЭ. Если пользователь нечаянно сломал строку — берём default. */
    val examDateParsed: LocalDate
        get() = runCatching { LocalDate.parse(examDate) }.getOrNull()
            ?: LocalDate.parse(DEFAULT_EXAM_DATE)

    /** Сколько дней осталось до даты ЕГЭ (отрицательное число = уже прошло). */
    fun daysUntilExam(today: LocalDate = LocalDate.now()): Int =
        ChronoUnit.DAYS.between(today, examDateParsed).toInt()

    /** Инициал для аватарки (первая буква имени), или null если имя пустое. */
    val initial: String?
        get() = name.trim().firstOrNull()?.uppercase()
}

private val Context.userProfileStore by preferencesDataStore("user_profile")

object UserProfileStore {
    private val NAME = stringPreferencesKey("name")
    private val BIRTH = stringPreferencesKey("birth_date")
    private val TARGET = intPreferencesKey("target_score")
    private val EXAM = stringPreferencesKey("exam_date")

    fun profileFlow(context: Context): Flow<UserProfile> =
        context.userProfileStore.data.map { prefs ->
            UserProfile(
                name = prefs[NAME] ?: "",
                birthDate = prefs[BIRTH],
                targetScore = prefs[TARGET] ?: 80,
                examDate = prefs[EXAM] ?: UserProfile.DEFAULT_EXAM_DATE,
            )
        }

    suspend fun snapshot(context: Context): UserProfile =
        profileFlow(context).first()

    suspend fun setName(context: Context, name: String) {
        context.userProfileStore.edit { it[NAME] = name.trim() }
    }

    suspend fun setBirthDate(context: Context, date: LocalDate) {
        context.userProfileStore.edit { it[BIRTH] = date.toString() }
    }

    suspend fun clearBirthDate(context: Context) {
        context.userProfileStore.edit { it.remove(BIRTH) }
    }

    suspend fun setTargetScore(context: Context, score: Int) {
        context.userProfileStore.edit { it[TARGET] = score.coerceIn(0, 100) }
    }

    suspend fun setExamDate(context: Context, date: LocalDate) {
        context.userProfileStore.edit { it[EXAM] = date.toString() }
    }

    /** Stage P3-A part Д: восстановление из бэкапа. */
    suspend fun restore(context: Context, profile: UserProfile) {
        context.userProfileStore.edit { prefs ->
            prefs[NAME] = profile.name
            if (profile.birthDate != null) prefs[BIRTH] = profile.birthDate else prefs.remove(BIRTH)
            prefs[TARGET] = profile.targetScore
            prefs[EXAM] = profile.examDate
        }
    }
}
