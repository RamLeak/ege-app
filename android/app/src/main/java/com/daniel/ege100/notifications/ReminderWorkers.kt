package com.daniel.ege100.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.daniel.ege100.data.AppSettingsStore
import com.daniel.ege100.data.DAILY_GOAL
import com.daniel.ege100.data.MockExamSchedule
import com.daniel.ege100.data.StreakStore
import com.daniel.ege100.data.UserProfileStore
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Phase 3 Stage FINAL part Б — 3 периодических воркера.
 *
 * Каждый воркер в начале проверяет соответствующий флаг AppSettings —
 * если выключено, возвращает Result.success без действий (Convention #34).
 *
 * WorkManager сам гарантирует, что воркер запустится примерно в указанное
 * время с учётом батарейных ограничений Android (DOZE). Точность около часа
 * — для напоминаний это приемлемо.
 */

/**
 * StreakReminderWorker — 20:00 ежедневно. Если за сегодня решено меньше
 * DAILY_GOAL (10) задач — push «Streak в опасности». Если стрейк = 0 и
 * сегодня вообще ничего не решено — push не приходит (не пытаемся
 * мотивировать с нуля через streak, есть отдельный DailyReminder).
 */
class StreakReminderWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val settings = AppSettingsStore.snapshot(ctx)
        if (!settings.notifyStreak) return Result.success()

        val state = StreakStore.snapshot(ctx)
        val solved = state.todaySolvedCount
        if (solved in 1 until DAILY_GOAL) {
            NotificationHelper.show(
                context = ctx,
                title = "🔥 Streak в опасности",
                text = "Сегодня решено $solved из $DAILY_GOAL задач. Доведи до конца — день не пропадёт зря.",
                notifId = NotificationHelper.NOTIF_STREAK,
            )
        }
        return Result.success()
    }
}

/**
 * DailyReminderWorker — 9:00 ежедневно. Если последняя активность >2 дней
 * назад — push «ждут задачи». Используется last_active из StreakStore
 * (обновляется в onProblemSolved).
 */
class DailyReminderWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val settings = AppSettingsStore.snapshot(ctx)
        if (!settings.notifyReminders) return Result.success()

        val state = StreakStore.snapshot(ctx)
        val lastActive = state.lastActiveDate?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
        } ?: return Result.success()  // пока ни одного активного дня — не спамим
        val gap = ChronoUnit.DAYS.between(lastActive, LocalDate.now())
        if (gap >= 2) {
            val profile = UserProfileStore.snapshot(ctx)
            val name = profile.name.trim().ifBlank { "Готовься" }
            NotificationHelper.show(
                context = ctx,
                title = "📚 $name, давай вернёмся",
                text = "Прошло $gap ${dayWord(gap.toInt())} с последней активности. Не пропускай — ЕГЭ ближе, чем кажется.",
                notifId = NotificationHelper.NOTIF_DAILY,
            )
        }
        return Result.success()
    }
}

/**
 * MockExamReminderWorker — 9:00 ежедневно. Если завтра пробник по расписанию —
 * push «Завтра пробник №N».
 */
class MockExamReminderWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val settings = AppSettingsStore.snapshot(ctx)
        if (!settings.notifyMockExams) return Result.success()

        val profile = UserProfileStore.snapshot(ctx)
        val tomorrow = LocalDate.now().plusDays(1)
        val nextPlan = MockExamSchedule.getNextMockExam(ctx, profile.examDateParsed)
            ?: return Result.success()
        if (nextPlan.parsedDate == tomorrow) {
            NotificationHelper.show(
                context = ctx,
                title = "📅 Завтра пробник №${nextPlan.index}",
                text = "Подготовься — 8 задач математики + 8 русского. Открой приложение и проверь радар слабых мест.",
                notifId = NotificationHelper.NOTIF_MOCK,
            )
        }
        return Result.success()
    }
}

private fun dayWord(n: Int): String {
    val n100 = n % 100
    val n10 = n % 10
    return when {
        n100 in 11..14 -> "дней"
        n10 == 1 -> "день"
        n10 in 2..4 -> "дня"
        else -> "дней"
    }
}
