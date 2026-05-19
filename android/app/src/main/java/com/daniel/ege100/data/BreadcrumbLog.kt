package com.daniel.ege100.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Phase 4 Stage P4-D2 part Г (Convention #67) — кольцевой буфер последних
 * действий пользователя для crash-отчёта.
 *
 * Зачем: глобальный crash handler (Convention #55) сейчас пишет в файл
 * stack trace, но без контекста невозможно понять что именно делал
 * пользователь перед падением. BreadcrumbLog хранит последние 20 событий
 * (навигация, проверка ответа, тапы AI и т.п.), которые включаются в
 * `EgeApplication.installCrashHandler` → crash log → пользователь
 * отправляет лог в Telegram.
 *
 * Thread-safety: @Synchronized + mutableListOf — переходов мало, lock
 * contention незначителен. Превышение MAX_SIZE → удаляем самый старый.
 *
 * Использование (см. подключения по проекту):
 *   - MainActivity.onCreate → "App started".
 *   - EgeApp NavHost → "Navigate to <route>".
 *   - ProblemDetailViewModel.check → "Check answer ...".
 *   - AskAiViewModel.ask → "Ask AI: <provider>/<model>".
 *   - AccentTrainerViewModel.tapSyllable → "AccentTap: ...".
 *   - WordBlankTrainerViewModel.check → "WordBlankCheck: ...".
 */
object BreadcrumbLog {
    private val breadcrumbs = ArrayDeque<String>()
    private const val MAX_SIZE = 20
    private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun add(action: String) {
        val safe = action.take(160)
        val timestamp = timeFmt.format(Date())
        breadcrumbs.addLast("[$timestamp] $safe")
        while (breadcrumbs.size > MAX_SIZE) breadcrumbs.removeFirst()
    }

    @Synchronized
    fun getRecent(): String = breadcrumbs.joinToString("\n")

    @Synchronized
    fun clear() {
        breadcrumbs.clear()
    }
}
