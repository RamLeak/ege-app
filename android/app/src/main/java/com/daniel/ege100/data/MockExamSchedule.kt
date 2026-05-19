package com.daniel.ege100.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Phase 3 Stage FINAL part А — расписание пробников.
 *
 * Алгоритм (Safety Rule #3 из CLAUDE.md):
 *   - install_date сохраняется при первом вызове `ensureInstallDate()`,
 *     персистится в DataStore.
 *   - Первый пробник: `max(install + 28 дней, today + 14 дней)` —
 *     даёт минимум 2 недели до первого даже если пользователь установил
 *     приложение незадолго до ЕГЭ.
 *   - Дальше каждые 21 день.
 *   - Последний пробник должен быть ≤ `examDate - 7` (за неделю до ЕГЭ).
 *   - Максимум 16 пробников (закроется естественно из-за examDate).
 *
 * install_date сохраняется навсегда — `resetProgress` его НЕ трогает
 * (Convention #33). Это техническая дата, не пользовательская.
 */

@Serializable
data class MockExamPlan(
    val index: Int,        // 1-based
    val date: String,      // ISO YYYY-MM-DD
    val status: MockExamStatus,
) {
    val parsedDate: LocalDate get() = LocalDate.parse(date)
}

enum class MockExamStatus { UPCOMING, TODAY, PAST }

private val Context.mockExamScheduleStore by preferencesDataStore("mock_exam_schedule")

object MockExamSchedule {
    private val INSTALL_DATE = stringPreferencesKey("install_date")

    /**
     * Возвращает дату установки приложения. При первом вызове сохраняет
     * текущую дату навсегда.
     */
    suspend fun ensureInstallDate(context: Context): LocalDate {
        val saved = context.mockExamScheduleStore.data
            .map { it[INSTALL_DATE]?.let { s -> runCatching { LocalDate.parse(s) }.getOrNull() } }
            .first()
        if (saved != null) return saved
        val today = LocalDate.now()
        context.mockExamScheduleStore.edit { it[INSTALL_DATE] = today.toString() }
        return today
    }

    /** Только чтение, без сохранения. null если ещё не было установки. */
    suspend fun peekInstallDate(context: Context): LocalDate? =
        context.mockExamScheduleStore.data
            .map { it[INSTALL_DATE]?.let { s -> runCatching { LocalDate.parse(s) }.getOrNull() } }
            .first()

    /** Backup-восстановление install_date. */
    suspend fun restoreInstallDate(context: Context, date: LocalDate) {
        context.mockExamScheduleStore.edit { it[INSTALL_DATE] = date.toString() }
    }

    /**
     * Список 16 пробников от install_date до examDate-7.
     */
    suspend fun getSchedule(context: Context, examDate: LocalDate): List<MockExamPlan> {
        val installDate = ensureInstallDate(context)
        val today = LocalDate.now()

        val firstFromInstall = installDate.plusDays(28)
        val firstFromToday = today.plusDays(14)
        val firstDate = if (firstFromInstall.isAfter(firstFromToday)) firstFromInstall else firstFromToday

        val lastAllowed = examDate.minusDays(7)

        val plans = mutableListOf<MockExamPlan>()
        var current = firstDate
        var index = 1
        while (!current.isAfter(lastAllowed) && plans.size < 16) {
            plans += MockExamPlan(
                index = index,
                date = current.toString(),
                status = computeStatus(current, today),
            )
            current = current.plusDays(21)
            index++
        }
        return plans
    }

    suspend fun getNextMockExam(context: Context, examDate: LocalDate): MockExamPlan? {
        val today = LocalDate.now()
        return getSchedule(context, examDate).firstOrNull { !it.parsedDate.isBefore(today) }
    }

    suspend fun getDaysUntilNext(context: Context, examDate: LocalDate): Int? {
        val next = getNextMockExam(context, examDate) ?: return null
        return ChronoUnit.DAYS.between(LocalDate.now(), next.parsedDate).toInt()
    }

    private fun computeStatus(planDate: LocalDate, today: LocalDate): MockExamStatus = when {
        planDate.isEqual(today) -> MockExamStatus.TODAY
        planDate.isBefore(today) -> MockExamStatus.PAST
        else -> MockExamStatus.UPCOMING
    }
}
