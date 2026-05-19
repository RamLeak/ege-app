package com.daniel.ege100

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.daniel.ege100.data.BreadcrumbLog
import com.daniel.ege100.data.CrashLog
import com.daniel.ege100.notifications.DailyReminderWorker
import com.daniel.ege100.notifications.MockExamReminderWorker
import com.daniel.ege100.notifications.NotificationHelper
import com.daniel.ege100.notifications.StreakReminderWorker
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Глобальный Coil ImageLoader с поддержкой SVG + регистрация WorkManager-
 * воркеров для напоминаний (Phase 3 Stage FINAL part Б, Convention #34).
 *
 * Coil:
 *   `coil-svg` подключён в Stage 1, но `SvgDecoder.Factory()` нужно явно
 *   зарегистрировать в ImageLoader'е приложения — Coil использует ленивую
 *   инициализацию глобального лоадера, без этого SVG не декодируется.
 *   Кеши: в памяти 25% от runtime maxMemory (формул много, лёгкие), на
 *   диске 100 MB в `cacheDir/svg_cache`.
 *
 * Workers:
 *   3 periodic workers (StreakReminder 20:00, DailyReminder 9:00,
 *   MockExamReminder 9:00). `ExistingPeriodicWorkPolicy.KEEP` —
 *   при следующих запусках приложения мы не пере-запускаем уже
 *   запланированные jobs (WorkManager хранит расписание сам).
 */
class EgeApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        installCrashHandler()
        BreadcrumbLog.add("App started")
        NotificationHelper.ensureChannel(this)
        scheduleReminderWorkers()
        cleanupOldAiCache()
    }

    /**
     * Phase 4 Stage P4-C2 part Г (Convention #55) — глобальный логгер
     * непойманных исключений.
     *
     * Phase 4 Stage P4-D2 part Г (Convention #67-68) — расширено: помимо
     * Log.e в logcat (доступен через adb, недоступен пользователю),
     * пишем подробный crash-report в `files/crashes/crash_<ts>.txt`. При
     * следующем запуске MainActivity видит флаг `last_crash_unhandled`
     * и показывает CrashRecoveryDialog с кнопкой «Отправить лог» —
     * пользователь сам передаёт stack trace разработчику без adb.
     */
    private fun installCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            android.util.Log.e(
                "EgeApp",
                "UNCAUGHT EXCEPTION on thread '${thread.name}'",
                exception,
            )
            // Не должны рекурсивно крашиться — все ошибки внутри
            // writeCrashReport проглочены через runCatching.
            CrashLog.writeCrashReport(this, thread, exception)
            defaultHandler?.uncaughtException(thread, exception)
        }
    }

    /** Phase 4 Stage A5: чистим AI-кеш старше 30 дней. */
    private fun cleanupOldAiCache() {
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
                com.daniel.ege100.data.UserDataDatabase.get(this@EgeApplication)
                    .aiResponseCacheDao()
                    .deleteOlderThan(cutoff)
            }
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(SvgDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("svg_cache"))
                    .maxSizeBytes(100L * 1024 * 1024)
                    .build()
            }
            .build()
    }

    private fun scheduleReminderWorkers() {
        val wm = WorkManager.getInstance(this)
        wm.enqueueUniquePeriodicWork(
            "streak_reminder",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<StreakReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delayUntil(20, 0), TimeUnit.MILLISECONDS)
                .build(),
        )
        wm.enqueueUniquePeriodicWork(
            "daily_reminder",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DailyReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delayUntil(9, 0), TimeUnit.MILLISECONDS)
                .build(),
        )
        wm.enqueueUniquePeriodicWork(
            "mock_exam_reminder",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<MockExamReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delayUntil(9, 0), TimeUnit.MILLISECONDS)
                .build(),
        )
    }

    /** Миллисекунд до следующего наступления локального времени `hour:minute`. */
    private fun delayUntil(hour: Int, minute: Int): Long {
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        var target = now.toLocalDate().atTime(LocalTime.of(hour, minute))
        if (!target.isAfter(now)) target = target.plusDays(1)
        val nowMs = now.atZone(zone).toInstant().toEpochMilli()
        val targetMs = target.atZone(zone).toInstant().toEpochMilli()
        return (targetMs - nowMs).coerceAtLeast(0L)
    }
}
