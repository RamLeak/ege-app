package com.daniel.ege100.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Phase 3 Stage FINAL part В — страховки из premortem.
 *
 * Реализованы 2 страховки (Convention #35):
 *
 * Rule #5 (50 задач/неделю): каждый понедельник `SafetyGuardsChecker.checkWeekly()`
 * считает кол-во попыток за прошлую календарную неделю (пн-вс). Если <50 —
 * `weeklyGuardActive = true` и счётчик отображается в красной карточке на
 * главном. Снимается автоматически когда в следующий понедельник окажется
 * >= 50 за прошлую неделю.
 *
 * Rule #6 (8-week checkpoint): каждые 8 недель от install_date считаем
 * сумму attempts за прошлые 8 недель. Если <300 — `eightWeekGuardActive = true`
 * → AlertDialog при следующем заходе на главный. Пользователь жмёт OK,
 * `setEightWeekGuard(false)` снимает флаг (одноразовая нотификация).
 * `lastCheckedPeriod` хранит индекс уже проверенного 8-нед периода чтобы
 * не дублировать.
 *
 * Это **политические** инструменты — пользователь видит, что подготовка
 * замедляется, но никаких реальных блокировок нет (Safety Rule #5 в CLAUDE
 * подразумевает «код заморожен», но это инструкция мне как Claude Code, не
 * фича приложения).
 */
@Serializable
data class GuardsState(
    val weeklyGuardActive: Boolean = false,
    val weeklyAttemptsLastWeek: Int = 0,
    val eightWeekGuardActive: Boolean = false,
    val eightWeekAttemptsLastPeriod: Int = 0,
    val lastCheckedEightWeekPeriod: Int = 0,
)

private val Context.safetyGuardsStore by preferencesDataStore("safety_guards")

object SafetyGuardsStore {
    private val WEEKLY_ACTIVE = booleanPreferencesKey("weekly_active")
    private val WEEKLY_COUNT = intPreferencesKey("weekly_count")
    private val EIGHT_ACTIVE = booleanPreferencesKey("eight_active")
    private val EIGHT_COUNT = intPreferencesKey("eight_count")
    private val EIGHT_LAST_PERIOD = intPreferencesKey("eight_last_period")

    fun stateFlow(context: Context): Flow<GuardsState> =
        context.safetyGuardsStore.data.map { prefs ->
            GuardsState(
                weeklyGuardActive = prefs[WEEKLY_ACTIVE] ?: false,
                weeklyAttemptsLastWeek = prefs[WEEKLY_COUNT] ?: 0,
                eightWeekGuardActive = prefs[EIGHT_ACTIVE] ?: false,
                eightWeekAttemptsLastPeriod = prefs[EIGHT_COUNT] ?: 0,
                lastCheckedEightWeekPeriod = prefs[EIGHT_LAST_PERIOD] ?: 0,
            )
        }

    suspend fun snapshot(context: Context): GuardsState = stateFlow(context).first()

    suspend fun setWeekly(context: Context, active: Boolean, count: Int) {
        context.safetyGuardsStore.edit {
            it[WEEKLY_ACTIVE] = active
            it[WEEKLY_COUNT] = count
        }
    }

    suspend fun setEightWeek(context: Context, active: Boolean, count: Int, period: Int) {
        context.safetyGuardsStore.edit {
            it[EIGHT_ACTIVE] = active
            it[EIGHT_COUNT] = count
            it[EIGHT_LAST_PERIOD] = period
        }
    }

    suspend fun dismissEightWeek(context: Context) {
        context.safetyGuardsStore.edit { it[EIGHT_ACTIVE] = false }
    }

    suspend fun restore(context: Context, state: GuardsState) {
        context.safetyGuardsStore.edit {
            it[WEEKLY_ACTIVE] = state.weeklyGuardActive
            it[WEEKLY_COUNT] = state.weeklyAttemptsLastWeek
            it[EIGHT_ACTIVE] = state.eightWeekGuardActive
            it[EIGHT_COUNT] = state.eightWeekAttemptsLastPeriod
            it[EIGHT_LAST_PERIOD] = state.lastCheckedEightWeekPeriod
        }
    }

    suspend fun clearAll(context: Context) {
        context.safetyGuardsStore.edit { it.clear() }
    }
}

object SafetyGuardsChecker {
    const val WEEKLY_THRESHOLD = 50
    const val EIGHT_WEEK_THRESHOLD = 300

    /**
     * Проверка #5. Запускается на старте Главного экрана. Реальная
     * проверка происходит **только в понедельник** (или если ещё не было
     * проверки за последнюю неделю — мы не привязываемся к точному дню
     * запуска, важна последняя календарная неделя пн-вс).
     */
    suspend fun checkWeekly(context: Context, attemptDao: AttemptLogDao) {
        val today = LocalDate.now()
        // Начало прошлой календарной недели (понедельник предыдущей).
        val startOfThisWeek = today.with(DayOfWeek.MONDAY)
        val startOfPrevWeek = startOfThisWeek.minusWeeks(1)
        val zone = ZoneId.systemDefault()
        val startMs = startOfPrevWeek.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMs = startOfThisWeek.atStartOfDay(zone).toInstant().toEpochMilli()
        val count = attemptDao.getCountBetween(startMs, endMs)
        SafetyGuardsStore.setWeekly(context, active = count < WEEKLY_THRESHOLD, count = count)
    }

    /**
     * Проверка #6. Каждые 8 недель от install_date. Если для текущего
     * 8-недельного «слота» (например, install + 8w, install + 16w, ...)
     * мы ещё не проверяли (`lastCheckedEightWeekPeriod < currentPeriod`),
     * считаем сумму за прошлый период. При <300 — активируем.
     */
    suspend fun checkEightWeek(context: Context, attemptDao: AttemptLogDao) {
        val installDate = MockExamSchedule.peekInstallDate(context) ?: return
        val today = LocalDate.now()
        val weeksSinceInstall = ChronoUnit.WEEKS.between(installDate, today).toInt()
        if (weeksSinceInstall < 8) return
        val currentPeriod = weeksSinceInstall / 8
        val previous = SafetyGuardsStore.snapshot(context)
        if (previous.lastCheckedEightWeekPeriod >= currentPeriod) return

        val periodEnd = installDate.plusWeeks((currentPeriod * 8L))
        val periodStart = installDate.plusWeeks((currentPeriod * 8L) - 8L)
        val zone = ZoneId.systemDefault()
        val startMs = periodStart.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMs = periodEnd.atStartOfDay(zone).toInstant().toEpochMilli()
        val count = attemptDao.getCountBetween(startMs, endMs)
        SafetyGuardsStore.setEightWeek(
            context = context,
            active = count < EIGHT_WEEK_THRESHOLD,
            count = count,
            period = currentPeriod,
        )
    }
}
