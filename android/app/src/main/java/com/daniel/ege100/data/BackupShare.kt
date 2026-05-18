package com.daniel.ege100.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDate

/**
 * Phase 3 Stage A part Д3-Д4 — share/load JSON-бэкапа через системные интенты.
 *
 * Share-sheet: создаёт временный файл в cacheDir + FileProvider URI +
 * Intent.ACTION_SEND с MIME application/json. Пользователь выбирает Telegram,
 * Drive, локальные файлы и т.п.
 *
 * Импорт: ActivityResultContracts.GetContent открывает системный picker;
 * выбранный URI читаем через contentResolver.openInputStream — это работает
 * для файлов из Drive, Telegram (cached), локальных и т.д.
 */
object BackupShare {
    private const val AUTHORITY_SUFFIX = ".fileprovider"
    private const val FILE_PREFIX = "ege100_backup_"

    /**
     * Пишет [json] во временный файл и возвращает Intent.createChooser
     * готовый к startActivity.
     */
    fun buildShareIntent(context: Context, json: String): Intent {
        val fileName = "${FILE_PREFIX}${LocalDate.now()}.json"
        val file = File(context.cacheDir, fileName).apply {
            // Перезаписываем если уже есть от прошлого экспорта в этот же день.
            writeText(json, Charsets.UTF_8)
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}$AUTHORITY_SUFFIX",
            file,
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Резервная копия EGE100")
            putExtra(Intent.EXTRA_TEXT, "Прогресс EGE100 от ${LocalDate.now()}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Сохранить резервную копию")
    }

    /** Читает текстовое содержимое URI (для импорта из picker'а). */
    fun readUriContent(context: Context, uri: android.net.Uri): Result<String> = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader(Charsets.UTF_8).readText()
        } ?: throw IllegalStateException("Не удалось открыть файл")
    }
}
