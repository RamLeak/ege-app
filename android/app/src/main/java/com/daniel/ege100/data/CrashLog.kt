package com.daniel.ege100.data

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Phase 4 Stage P4-D2 part Г (Convention #68) — менеджер crash-логов.
 *
 * Хранилище: `context.filesDir/crashes/crash_<ts>.txt`. Эти файлы НЕ
 * экспортируются Auto Backup'ом — путь `files/` доступен только локально,
 * вне списков `<include domain="...">` в `backup_rules.xml`.
 *
 * Поток через состояние:
 *   1. EgeApplication.installCrashHandler пишет файл + флаг `last_crash_unhandled`.
 *   2. MainActivity.onCreate проверяет флаг → если true, ставит state в Compose
 *      и показывает CrashRecoveryDialog → пользователь жмёт «Отправить лог».
 *   3. CrashLog.share() через FileProvider открывает Intent.ACTION_SEND.
 *   4. CrashLog.clearAll() удаляет все логи (после успешной отправки).
 *
 * Дублирующая кнопка в SettingsScreen — для случая когда пользователь
 * случайно закрыл диалог при предыдущем запуске.
 */
object CrashLog {
    private const val DIR_NAME = "crashes"
    private const val PREFS_NAME = "crash_state"
    private const val KEY_UNHANDLED = "last_crash_unhandled"
    private const val KEY_LAST_FILE = "last_crash_file"
    private const val MAX_FILES = 5
    private const val MAX_TRACE_CHARS = 8000

    private val fileTimeFmt = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)

    fun dir(context: Context): File {
        val d = File(context.filesDir, DIR_NAME)
        if (!d.exists()) d.mkdirs()
        return d
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Вызывается из crash handler'а в EgeApplication. Запись делаем
     * максимально defensive — нельзя падать рекурсивно.
     */
    fun writeCrashReport(context: Context, thread: Thread, exception: Throwable) {
        runCatching {
            val ts = System.currentTimeMillis()
            val stamp = fileTimeFmt.format(Date(ts))
            // Версия приложения — через PackageManager (BuildConfig в проекте
            // не включён). На любых ошибках чтения PackageInfo даём «unknown».
            val (vName, vCode) = runCatching {
                val pkg = context.packageName
                val info = context.packageManager.getPackageInfo(pkg, 0)
                val name = info.versionName ?: "unknown"
                val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
                else @Suppress("DEPRECATION") info.versionCode.toLong()
                name to code.toString()
            }.getOrDefault("unknown" to "0")
            val report = buildString {
                append("EGE100 Crash Report\n")
                append("====================\n")
                append("Time: ").append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(ts))).append("\n")
                append("Thread: ").append(thread.name).append("\n")
                append("App version: ").append(vName).append(" (").append(vCode).append(")\n")
                append("Android: ").append(Build.VERSION.RELEASE).append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n")
                append("Device: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n")
                append("====================\n")
                append("Exception: ").append(exception.javaClass.name).append("\n")
                append("Message: ").append(exception.message ?: "(null)").append("\n")
                append("====================\n")
                append("Stack trace:\n")
                append(exception.stackTraceToString().take(MAX_TRACE_CHARS))
                append("\n====================\n")
                append("Recent app actions (last ${20}):\n")
                append(BreadcrumbLog.getRecent())
                append("\n")
            }
            val file = File(dir(context), "crash_${stamp}_$ts.txt")
            file.writeText(report)
            trimOldFiles(context)
            prefs(context).edit()
                .putBoolean(KEY_UNHANDLED, true)
                .putString(KEY_LAST_FILE, file.absolutePath)
                .apply()
        }.onFailure {
            android.util.Log.e("EgeApp", "writeCrashReport failed", it)
        }
    }

    /** Удаляет старые файлы — оставляет MAX_FILES самых свежих. */
    private fun trimOldFiles(context: Context) {
        runCatching {
            val files = dir(context).listFiles()?.toList()?.sortedByDescending { it.lastModified() }
                ?: return
            files.drop(MAX_FILES).forEach { it.delete() }
        }
    }

    /** Есть ли необработанный краш (флаг от прошлой сессии). */
    fun hasUnhandledCrash(context: Context): Boolean =
        prefs(context).getBoolean(KEY_UNHANDLED, false)

    fun lastCrashFile(context: Context): File? {
        val path = prefs(context).getString(KEY_LAST_FILE, null) ?: return null
        val f = File(path)
        return if (f.exists()) f else null
    }

    /** Сброс флага — после показа диалога. */
    fun clearUnhandledFlag(context: Context) {
        prefs(context).edit()
            .remove(KEY_UNHANDLED)
            .remove(KEY_LAST_FILE)
            .apply()
    }

    /** Все crash-файлы в директории, сортировка от свежих к старым. */
    fun listFiles(context: Context): List<File> {
        return runCatching {
            dir(context).listFiles()?.toList()?.sortedByDescending { it.lastModified() } ?: emptyList()
        }.getOrDefault(emptyList())
    }

    fun deleteAll(context: Context) {
        runCatching {
            dir(context).listFiles()?.forEach { it.delete() }
            prefs(context).edit().clear().apply()
        }
    }

    /**
     * Открыть system share-sheet с конкретным crash-файлом через FileProvider.
     * Если файл не найден — возвращает false (caller покажет Toast).
     */
    fun share(context: Context, file: File): Boolean {
        if (!file.exists()) return false
        return runCatching {
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "EGE100 Crash Report")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Отправить crash log").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            true
        }.getOrDefault(false)
    }

    /** Делит самый свежий лог. */
    fun shareLatest(context: Context): Boolean {
        val latest = listFiles(context).firstOrNull() ?: return false
        return share(context, latest)
    }
}
