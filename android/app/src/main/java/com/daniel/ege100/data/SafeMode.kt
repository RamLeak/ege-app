package com.daniel.ege100.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Phase 4 Stage P4-D3 (Convention #69) — защита от crash-loop.
 *
 * Проблема: если краш происходит при старте приложения (например, в
 * `EgeApplication.onCreate` или в первом setContent MainActivity), пользователь
 * попадает в бесконечный цикл «открыл → крашнулся → открыл → крашнулся».
 * adb недоступен (Samsung без USB-отладки), CrashRecoveryDialog тоже не успевает
 * показаться, потому что setContent падает.
 *
 * Решение: считаем краши в скользящем 30-секундном окне. При 3+ крашах за окно —
 * ставим флаг `safe_mode_active`. MainActivity проверяет его в onCreate ДО
 * setContent основного приложения. Если флаг есть — рендерит минимальный UI
 * с кнопкой «Сбросить», которая чистит флаг и перезапускает Activity.
 *
 * Безопасный режим осознанно НЕ запускает:
 *   - EgeApp NavHost (с его 30+ экранами — любой может быть источником краша)
 *   - AppSettingsStore subscription (если DataStore повреждён — тоже краш)
 *   - corpus.db через Room (если миграция сломалась — тоже краш)
 *
 * Только Material Surface + Text + Button. Минимум поверхности для краша.
 */
object SafeMode {
    private const val PREFS_NAME = "safe_mode_state"
    private const val KEY_TIMESTAMPS = "crash_timestamps"
    private const val KEY_ACTIVE = "safe_mode_active"
    /** Сколько крашей за окно достаточно, чтобы включить safe-mode. */
    private const val CRASH_LIMIT = 3
    /** Размер окна — 30 секунд. Короче → ложные триггеры при тапах подряд. */
    private const val WINDOW_MS = 30_000L

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Зафиксировать факт краша. Вызывается из глобального
     * UncaughtExceptionHandler. Все ошибки внутри проглатываем — нельзя
     * рекурсивно крашиться из crash handler'а.
     */
    fun recordCrash(context: Context) {
        runCatching {
            val now = System.currentTimeMillis()
            val cutoff = now - WINDOW_MS
            val raw = prefs(context).getString(KEY_TIMESTAMPS, "") ?: ""
            val recent = raw.split(",")
                .mapNotNull { it.trim().toLongOrNull() }
                .filter { it >= cutoff }
                .toMutableList()
            recent.add(now)
            val active = recent.size >= CRASH_LIMIT
            prefs(context).edit()
                .putString(KEY_TIMESTAMPS, recent.joinToString(","))
                .putBoolean(KEY_ACTIVE, active)
                .apply()
        }.onFailure {
            android.util.Log.e("SafeMode", "recordCrash failed", it)
        }
    }

    fun isActive(context: Context): Boolean =
        runCatching { prefs(context).getBoolean(KEY_ACTIVE, false) }.getOrDefault(false)

    /** Очистить флаг + историю timestamp'ов. Вызывается из UI после «Сбросить». */
    fun reset(context: Context) {
        runCatching {
            prefs(context).edit()
                .remove(KEY_ACTIVE)
                .remove(KEY_TIMESTAMPS)
                .apply()
        }
    }
}
